INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Amul Taaza Milk 1L', 'Dairy', 'Amul', '8901262010012', 28.00, 32.00, 18, 10
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901262010012');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Basmati Rice 5kg', 'Grocery', 'Fortune', '8906010501123', 380.00, 450.00, 24, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8906010501123');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Harvest Gold Wheat Bread', 'Bakery', 'Harvest Gold', '8904022001045', 36.00, 45.00, 5, 8
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8904022001045');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Shimla Fresh Apples 1kg', 'Produce', 'Fresh Farm', '8900010002011', 140.00, 180.00, 12, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8900010002011');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Tata Salt 1kg', 'Grocery', 'Tata', '8901058000105', 22.00, 28.00, 45, 10
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901058000105');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Coca Cola 750ml', 'Beverages', 'Coca Cola', '8901764012214', 31.00, 40.00, 18, 6
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901764012214');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Aashirvaad Whole Wheat Atta 10kg', 'Grocery', 'ITC', '8901058852315', 380.00, 440.00, 15, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901058852315');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Amul Butter 100g', 'Dairy', 'Amul', '8901262020059', 48.00, 56.00, 3, 8
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901262020059');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Mother Dairy Curd 400g', 'Dairy', 'Mother Dairy', '8901262030010', 28.00, 35.00, 2, 6
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901262030010');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Fortune Sunlite Sunflower Oil 1L', 'Grocery', 'Fortune', '8906010502211', 125.00, 145.00, 0, 10
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8906010502211');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Red Label Tea 500g', 'Beverages', 'Brooke Bond', '8901030012541', 240.00, 285.00, 16, 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901030012541');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Maggi 2-Min Noodles 4-Pack', 'Grocery', 'Nestle', '8901058863214', 48.00, 58.00, 30, 10
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901058863214');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Fresh Farm Tomatoes 1kg', 'Produce', 'Local Farm', '8900010003321', 25.00, 38.00, 22, 8
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8900010003321');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Surf Excel Easy Wash 1kg', 'Household', 'Unilever', '8901030745210', 118.00, 140.00, 14, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901030745210');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Dettol Original Soap 125g (3+1)', 'Personal Care', 'Reckitt', '8901396001245', 132.00, 160.00, 11, 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901396001245');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Parle-G Gold Biscuits 250g', 'Bakery', 'Parle', '8901030800012', 22.00, 30.00, 40, 10
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901030800012');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Everest Garam Masala 100g', 'Grocery', 'Everest', '8901234500987', 65.00, 78.00, 25, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901234500987');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Kissan Tomato Ketchup 950g', 'Grocery', 'Kissan', '8901030345671', 110.00, 135.00, 14, 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901030345671');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Haldiram Bhujia Sev 400g', 'Bakery', 'Haldiram', '8904012398712', 95.00, 115.00, 20, 6
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8904012398712');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Tropicana 100% Orange Juice 1L', 'Beverages', 'Tropicana', '8901456987123', 105.00, 130.00, 8, 4
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901456987123');
INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock)
SELECT 'Cadbury Dairy Milk Silk 150g', 'Bakery', 'Cadbury', '8901234999888', 140.00, 175.00, 16, 5
WHERE NOT EXISTS (SELECT 1 FROM products WHERE barcode = '8901234999888');
