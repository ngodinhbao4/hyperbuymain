package com.example.voucher.controller;

import com.example.voucher.entity.UserVoucher;
import com.example.voucher.entity.Voucher;
import com.example.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // ✅ Admin tạo voucher
    @PostMapping
    public ResponseEntity<Voucher> create(@RequestBody Voucher voucher) {
        return ResponseEntity.ok(voucherService.createVoucher(voucher));
    }

    // ✅ Xem tất cả voucher (cho admin hoặc public)
    @GetMapping
    public ResponseEntity<List<Voucher>> all() {
        return ResponseEntity.ok(voucherService.getAllVouchers());
    }

    // ✅ Phát voucher cho user (gọi từ MiniGame hoặc admin)
    @PostMapping("/issue/{userId}")
    public ResponseEntity<UserVoucher> issue(
            @PathVariable String userId,
            @RequestParam String code
    ) {
        return ResponseEntity.ok(voucherService.issueVoucherToUser(userId, code));
    }

    // ✅ Lấy danh sách voucher của user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserVoucher>> getUserVouchers(@PathVariable String userId) {
        return ResponseEntity.ok(voucherService.getUserVouchers(userId));
    }
}
