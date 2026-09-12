package com.paytm.wallet.controller;

import com.paytm.wallet.domain.Transfer;
import com.paytm.wallet.domain.TransferStatus;
import com.paytm.wallet.dto.TransferRequest;
import com.paytm.wallet.dto.TransferResponse;
import com.paytm.wallet.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferService.transfer(request);
        HttpStatus status = transfer.status() == TransferStatus.DECLINED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse getById(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getById(id));
    }
}
