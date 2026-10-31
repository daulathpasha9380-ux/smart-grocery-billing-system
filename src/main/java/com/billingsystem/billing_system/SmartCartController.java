package com.billingsystem.billing_system;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/smart-cart")
@PreAuthorize("hasAnyRole('OWNER', 'CASHIER', 'CUSTOMER')")
public class SmartCartController {

    private final VoiceSmartCartService voiceService;
    private final PhotoSmartCartService photoService;

    public SmartCartController(VoiceSmartCartService voiceService, PhotoSmartCartService photoService) {
        this.voiceService = voiceService;
        this.photoService = photoService;
    }

    public record VoiceParseRequest(String command) {}

    @PostMapping(value = {"/voice/parse", "/command"})
    public ResponseEntity<VoiceSmartCartService.VoiceCommandResult> parseVoiceCommand(
            @RequestBody(required = false) VoiceParseRequest request,
            @RequestParam(name = "command", required = false) String queryParamCommand) {

        String command = "";
        if (request != null && request.command() != null) {
            command = request.command();
        } else if (queryParamCommand != null) {
            command = queryParamCommand;
        }

        VoiceSmartCartService.VoiceCommandResult result = voiceService.processCommand(command);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = {"/photo/recognize", "/recognize"})
    public ResponseEntity<PhotoSmartCartService.PhotoRecognitionResult> recognizePhoto(
            @RequestParam("image") MultipartFile image) {
        PhotoSmartCartService.PhotoRecognitionResult result = photoService.recognizeProduct(image);
        return ResponseEntity.ok(result);
    }
}
