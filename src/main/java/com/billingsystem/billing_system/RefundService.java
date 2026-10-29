package com.billingsystem.billing_system;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final JdbcTemplate jdbc;

    public RefundService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public RefundView createRefund(String invoice, RefundRequest request, Principal principal, boolean owner) {
        if (request == null || request.operationId() == null || request.operationId().isBlank()
                || request.operationId().length() > 80 || request.reason() == null
                || request.reason().isBlank() || request.reason().trim().length() > 500
                || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An operation ID, return reason, and at least one item are required.");
        }

        SaleHeader sale = jdbc.query("""
                SELECT id, cashier_username, customer_username, subtotal, discount, tax, total
                FROM transactions
                WHERE invoice = ? AND transaction_type = 'SALE'
                FOR UPDATE
                """, (rs, row) -> new SaleHeader(rs.getLong("id"), rs.getString("cashier_username"),
                rs.getString("customer_username"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"), rs.getBigDecimal("tax"),
                rs.getBigDecimal("total")), invoice).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found."));
        if (!owner && !sale.cashierUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found.");
        }

        RefundView existing = refundByOperationId(request.operationId());
        if (existing != null) {
            if (!existing.invoice().equals(invoice) || !existing.approvedBy().equals(principal.getName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This operation ID has already been used.");
            }
            return existing;
        }

        Set<Long> seenProducts = new HashSet<>();
        List<RefundLine> lines = new ArrayList<>();
        BigDecimal refundTotal = ZERO;
        for (RefundLineRequest requested : request.items()) {
            if (requested == null || requested.productId() <= 0 || requested.quantity() <= 0
                    || !seenProducts.add(requested.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Return items must have unique product IDs and positive quantities.");
            }
            SoldLine sold = jdbc.query("""
                    SELECT ti.id, ti.product_id, ti.unit_price, ti.quantity, ti.line_total,
                        COALESCE((SELECT SUM(ri.quantity) FROM refund_items ri
                                  WHERE ri.original_item_id = ti.id), 0) AS returned_quantity,
                        COALESCE((SELECT SUM(ri.refund_amount) FROM refund_items ri
                                  WHERE ri.original_item_id = ti.id), 0) AS refunded_amount
                    FROM transaction_items ti
                    WHERE ti.transaction_id = ? AND ti.product_id = ?
                    """, (rs, row) -> new SoldLine(rs.getLong("id"), rs.getLong("product_id"),
                    rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total"),
                    rs.getInt("returned_quantity"), rs.getBigDecimal("refunded_amount")),
                    sale.id(), requested.productId()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A returned product was not part of the original sale."));

            int remaining = sold.quantity() - sold.returnedQuantity();
            if (requested.quantity() > remaining) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The requested return quantity exceeds the quantity remaining on the invoice.");
            }
            BigDecimal allocatedDiscount = allocate(sale.discount(), sold.lineTotal(), sale.subtotal());
            BigDecimal allocatedTax = allocate(sale.tax(), sold.lineTotal(), sale.subtotal());
            BigDecimal refundableLineTotal = sold.lineTotal().subtract(allocatedDiscount).add(allocatedTax)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = requested.quantity() == remaining
                    ? refundableLineTotal.subtract(sold.refundedAmount()).setScale(2, RoundingMode.HALF_UP)
                    : refundableLineTotal.multiply(BigDecimal.valueOf(requested.quantity()))
                            .divide(BigDecimal.valueOf(sold.quantity()), 2, RoundingMode.HALF_UP);
            if (amount.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The refundable amount is invalid.");
            }
            lines.add(new RefundLine(sold, requested.quantity(), amount, requested.restock()));
            refundTotal = refundTotal.add(amount);
        }

                BigDecimal priorRefundTotal = jdbc.queryForObject(
                                "SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE transaction_id = ?",
                                BigDecimal.class, sale.id());
                if (priorRefundTotal.add(refundTotal).compareTo(sale.total()) > 0) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "The invoice has no refundable amount remaining.");
                }

        String refundReference = "REF-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refunds (operation_id, refund_reference, transaction_id, approved_by, reason, amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """, request.operationId(), refundReference, sale.id(), principal.getName(),
                request.reason().trim(), refundTotal);
        long refundId = jdbc.queryForObject("SELECT id FROM refunds WHERE operation_id = ?", Long.class,
                request.operationId());

        for (RefundLine line : lines) {
            jdbc.update("""
                    INSERT INTO refund_items (refund_id, original_item_id, product_id, quantity, refund_amount, restocked)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, refundId, line.sold().id(), line.sold().productId(), line.quantity(), line.amount(),
                    line.restock());
            if (line.restock()) {
                jdbc.update("UPDATE products SET stock = stock + ? WHERE id = ?",
                        line.quantity(), line.sold().productId());
                jdbc.update("""
                        INSERT INTO stock_movements
                            (product_id, actor_username, movement_type, quantity_change, reference_invoice, reason)
                        VALUES (?, ?, 'REFUND', ?, ?, ?)
                        """, line.sold().productId(), principal.getName(), line.quantity(), invoice,
                        request.reason().trim());
            }
        }
                adjustLoyaltyForRefund(sale, refundId, refundReference, priorRefundTotal.add(refundTotal));
        return refundById(refundId);
    }

        private void adjustLoyaltyForRefund(SaleHeader sale, long refundId, String refundReference,
                                                                                BigDecimal cumulativeRefundAmount) {
                if (sale.customerUsername() == null || sale.customerUsername().isBlank()) return;

                PointTotals originalPoints = jdbc.query("""
                                SELECT COALESCE(SUM(CASE WHEN entry_type = 'EARN' THEN points_delta ELSE 0 END), 0) AS earned,
                                        COALESCE(SUM(CASE WHEN entry_type = 'REDEEM' THEN -points_delta ELSE 0 END), 0) AS redeemed,
                                        COALESCE(SUM(CASE WHEN entry_type = 'REFUND_EARN_REVERSAL' THEN -points_delta ELSE 0 END), 0) AS reversed,
                                        COALESCE(SUM(CASE WHEN entry_type = 'REFUND_REDEMPTION_RESTORE' THEN points_delta ELSE 0 END), 0) AS restored
                                FROM loyalty_transactions WHERE transaction_id = ?
                        """, (rs, row) -> new PointTotals(rs.getInt("earned"), rs.getInt("redeemed"),
                                rs.getInt("reversed"), rs.getInt("restored")), sale.id()).get(0);
                int targetReversed = proportionalPoints(originalPoints.earned(), cumulativeRefundAmount, sale.total());
                int targetRestored = proportionalPoints(originalPoints.redeemed(), cumulativeRefundAmount, sale.total());
                int pointsReversed = Math.max(0, targetReversed - originalPoints.reversed());
                int pointsRestored = Math.max(0, targetRestored - originalPoints.restored());
                if (pointsReversed == 0 && pointsRestored == 0) return;

                jdbc.update("UPDATE app_users SET loyalty_points = loyalty_points + ? WHERE username = ? AND role = 'CUSTOMER'",
                                pointsRestored - pointsReversed, sale.customerUsername());
                if (pointsReversed > 0) {
                        jdbc.update("""
                                        INSERT INTO loyalty_transactions
                                                (reference_key, username, transaction_id, refund_id, entry_type, points_delta, reason)
                                        VALUES (?, ?, ?, ?, 'REFUND_EARN_REVERSAL', ?, 'Earned points reversed after refund')
                                        """, "refund:" + refundId + ":earned", sale.customerUsername(), sale.id(), refundId,
                                        -pointsReversed);
                }
                if (pointsRestored > 0) {
                        jdbc.update("""
                                        INSERT INTO loyalty_transactions
                                                (reference_key, username, transaction_id, refund_id, entry_type, points_delta, reason)
                                        VALUES (?, ?, ?, ?, 'REFUND_REDEMPTION_RESTORE', ?, 'Redeemed points restored after refund')
                                        """, "refund:" + refundId + ":redeemed", sale.customerUsername(), sale.id(), refundId,
                                        pointsRestored);
                }
        }

        private static int proportionalPoints(int points, BigDecimal refundedAmount, BigDecimal saleAmount) {
                if (points <= 0 || saleAmount.signum() <= 0) return 0;
                BigDecimal boundedRefund = refundedAmount.min(saleAmount);
                return BigDecimal.valueOf(points).multiply(boundedRefund)
                                .divide(saleAmount, 0, RoundingMode.DOWN).intValueExact();
        }

    public List<RefundView> refundsForInvoice(String invoice, Principal principal, boolean owner) {
        String sql = """
                SELECT r.id, r.refund_reference, t.invoice, r.amount, r.reason, r.approved_by, r.created_at
                FROM refunds r JOIN transactions t ON t.id = r.transaction_id
                WHERE t.invoice = ?
                """ + (owner ? "" : " AND t.cashier_username = ?") + " ORDER BY r.created_at DESC";
        List<RefundHeader> headers = owner
                ? jdbc.query(sql, REFUND_HEADER_MAPPER, invoice)
                : jdbc.query(sql, REFUND_HEADER_MAPPER, invoice, principal.getName());
        if (headers.isEmpty()) {
            boolean saleExists = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
                    FROM transactions WHERE invoice = ? AND transaction_type = 'SALE'
                    """, Boolean.class, invoice));
            if (!saleExists) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found.");
            if (!owner) {
                Integer owned = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM transactions
                        WHERE invoice = ? AND cashier_username = ? AND transaction_type = 'SALE'
                        """, Integer.class, invoice, principal.getName());
                if (owned == null || owned == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found.");
            }
        }
        return headers.stream().map(this::withItems).toList();
    }

    private RefundView refundByOperationId(String operationId) {
        List<RefundHeader> matches = jdbc.query("""
                SELECT r.id, r.refund_reference, t.invoice, r.amount, r.reason, r.approved_by, r.created_at
                FROM refunds r JOIN transactions t ON t.id = r.transaction_id
                WHERE r.operation_id = ?
                """, REFUND_HEADER_MAPPER, operationId);
        return matches.isEmpty() ? null : withItems(matches.get(0));
    }

    private RefundView refundById(long id) {
        RefundHeader header = jdbc.queryForObject("""
                SELECT r.id, r.refund_reference, t.invoice, r.amount, r.reason, r.approved_by, r.created_at
                FROM refunds r JOIN transactions t ON t.id = r.transaction_id WHERE r.id = ?
                """, REFUND_HEADER_MAPPER, id);
        return withItems(header);
    }

    private RefundView withItems(RefundHeader header) {
        List<RefundItemView> items = jdbc.query("""
                SELECT ri.product_id, ti.product_name, ri.quantity, ri.refund_amount, ri.restocked
                FROM refund_items ri JOIN transaction_items ti ON ti.id = ri.original_item_id
                WHERE ri.refund_id = ? ORDER BY ri.id
                """, (rs, row) -> new RefundItemView(rs.getLong("product_id"), rs.getString("product_name"),
                rs.getInt("quantity"), rs.getBigDecimal("refund_amount"), rs.getBoolean("restocked")), header.id());
        return new RefundView(header.reference(), header.invoice(), header.amount(), header.reason(),
                header.approvedBy(), header.createdAt(), items);
    }

    private static BigDecimal allocate(BigDecimal total, BigDecimal lineAmount, BigDecimal subtotal) {
        if (subtotal.signum() == 0) return ZERO;
        return total.multiply(lineAmount).divide(subtotal, 2, RoundingMode.HALF_UP);
    }

    public record RefundRequest(String operationId, String reason, List<RefundLineRequest> items) {}
    public record RefundLineRequest(long productId, int quantity, boolean restock) {}
    public record RefundView(String refundReference, String invoice, BigDecimal amount, String reason,
                             String approvedBy, LocalDateTime date, List<RefundItemView> items) {}
    public record RefundItemView(long productId, String name, int quantity, BigDecimal amount, boolean restocked) {}

        private record SaleHeader(long id, String cashierUsername, String customerUsername,
                                                          BigDecimal subtotal, BigDecimal discount,
                              BigDecimal tax, BigDecimal total) {}
    private record SoldLine(long id, long productId, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                            int returnedQuantity, BigDecimal refundedAmount) {}
    private record RefundLine(SoldLine sold, int quantity, BigDecimal amount, boolean restock) {}
    private record RefundHeader(long id, String reference, String invoice, BigDecimal amount, String reason,
                                String approvedBy, LocalDateTime createdAt) {}
        private record PointTotals(int earned, int redeemed, int reversed, int restored) {}

    private static final org.springframework.jdbc.core.RowMapper<RefundHeader> REFUND_HEADER_MAPPER = (rs, row) ->
            new RefundHeader(rs.getLong("id"), rs.getString("refund_reference"), rs.getString("invoice"),
                    rs.getBigDecimal("amount"), rs.getString("reason"), rs.getString("approved_by"),
                    rs.getTimestamp("created_at").toLocalDateTime());
}