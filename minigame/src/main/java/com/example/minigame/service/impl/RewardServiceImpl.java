package com.example.minigame.service.impl;

import com.example.minigame.client.VoucherClient;
import com.example.minigame.dto.RewardInfoDTO;
import com.example.minigame.dto.UserVoucherDTO;
import com.example.minigame.entity.LoyaltyAccount;
import com.example.minigame.repository.LoyaltyAccountRepository;
import com.example.minigame.service.RewardService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardServiceImpl implements RewardService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final VoucherClient voucherClient;

    /**
     * 🪙 Cộng điểm cho user (nếu chưa có tài khoản thì tạo mới)
     */
    @Override
    @Transactional
    public void addPoints(String userId, int points) {
        LoyaltyAccount account = loyaltyAccountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    LoyaltyAccount newAcc = new LoyaltyAccount();
                    newAcc.setUserId(userId);
                    newAcc.setPoints(0);
                    return loyaltyAccountRepository.save(newAcc);
                });

        account.setPoints(account.getPoints() + points);
        loyaltyAccountRepository.save(account);

        log.info("✅ Đã cộng {} điểm cho user {}", points, userId);
    }

    /**
     * 💱 Đổi điểm sang voucher (nếu đủ điểm)
     */
    @Override
    @Transactional
    public void redeemVoucher(String userId, String code) {
        LoyaltyAccount account = loyaltyAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản điểm của người dùng."));

        if (account.getPoints() < 100) {
            throw new RuntimeException("Không đủ điểm để đổi voucher! Cần ít nhất 100 điểm.");
        }

        // Trừ điểm
        account.setPoints(account.getPoints() - 100);
        loyaltyAccountRepository.save(account);

        // Gọi sang voucher-service để phát voucher
        voucherClient.issueVoucher(userId, code);

        log.info("🎁 Người dùng {} đã đổi 100 điểm để nhận voucher {}", userId, code);
    }

    /**
     * 🎡 Khi quay trúng thưởng
     */
    @Override
    @Transactional
    public void grantVoucherAfterSpin(String userId, String voucherCode) {
        try {
            voucherClient.issueVoucher(userId, voucherCode);
            log.info("🎊 Người chơi {} nhận được voucher {} sau khi quay thưởng", userId, voucherCode);
        } catch (Exception e) {
            log.error("❌ Lỗi khi phát voucher cho user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 📊 Lấy tổng kết điểm thưởng & số voucher (từ voucher-service)
     */
    @Override
    public RewardInfoDTO getRewardSummary(String userId) {
        LoyaltyAccount account = loyaltyAccountRepository.findByUserId(userId).orElse(null);
        int totalPoints = account != null ? account.getPoints() : 0;

        // Gọi sang voucher-service để đếm voucher thật
        List<UserVoucherDTO> vouchers = voucherClient.getUserVouchers(userId);
        int voucherCount = vouchers != null ? vouchers.size() : 0;

        return new RewardInfoDTO(userId, totalPoints, voucherCount);
    }

    /**
     * 🎟️ Lấy danh sách voucher của user (từ voucher-service)
     */
    @Override
    public List<UserVoucherDTO> getUserVouchers(String userId) {
        return voucherClient.getUserVouchers(userId);
    }
}
