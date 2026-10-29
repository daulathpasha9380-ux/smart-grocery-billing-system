package com.billingsystem.billing_system;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/smart-cart")
@PreAuthorize("hasAnyRole('OWNER', 'CASHIER', 'CUSTOMER')")
public class SmartCartController {
    private final SmartCartService smartCartService;
    private final ProductRecognitionService productRecognitionService;

    public SmartCartController(SmartCartService smartCartService,
                               ProductRecognitionService productRecognitionService) {
        this.smartCartService = smartCartService;
        this.productRecognitionService = productRecognitionService;
    }

    @PostMapping("/command")
    public SmartCartService.CommandResult interpretCommand(@RequestBody CommandRequest request) {
        if (request == null || request.command() == null || request.command().length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a command of up to 300 characters.");
        }
        SmartCartService.CommandResult result = smartCartService.interpret(request.command());
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Command not recognized. Try adding, removing, searching, changing quantity, or asking to view the cart.");
        }
        return result;
    }

    @PostMapping(value = {"/photo/recognize", "/recognize"})
    public ResponseEntity<ProductRecognitionService.RecognitionResult> recognizePhoto(
            @RequestParam("image") MultipartFile image) {
        ProductRecognitionService.RecognitionResult result = productRecognitionService.recognizeProduct(image);
        return ResponseEntity.ok(result);
    }

    public record CommandRequest(String command) {}
}
