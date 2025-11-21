package com.example.minigame.controller;

import com.example.minigame.dto.RewardInfoDTO;
import com.example.minigame.entity.MiniGameHistory;
import com.example.minigame.service.MiniGameHistoryService;
import com.example.minigame.service.MiniGameService;
import com.example.minigame.service.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;

/**
 * 🎮 MiniGame Controller
 *  - Đăng nhập nhận quà
 *  - Vòng quay may mắn
 *  - Xem lịch sử & tổng kết phần thưởng
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MiniGameController {

    private final MiniGameService miniGameService;
    private final MiniGameHistoryService historyService;
    private final RewardService rewardService;

    /**
     * 🎁 Đăng nhập nhận quà (1 lần mỗi ngày)
     */
    @PostMapping("/daily-reward/{userId}")
    public ResponseEntity<String> dailyReward(@PathVariable String userId) {
        if (historyService.hasClaimedToday(userId, "DAILY_REWARD")) {
            return ResponseEntity.badRequest().body("❌ Bạn đã nhận quà hôm nay rồi!");
        }

        // Cộng điểm
        rewardService.addPoints(userId, 10);

        // Lưu lịch sử
        historyService.saveHistory(userId, "DAILY_REWARD", "Nhận 10 điểm khi đăng nhập hôm nay");

        log.info("🎁 User {} nhận quà đăng nhập thành công (+10 điểm)", userId);
        return ResponseEntity.ok("✅ Nhận quà đăng nhập thành công! +10 điểm");
    }

    /**
     * 🎡 Vòng quay may mắn
     */
    @PostMapping("/spin/{userId}")
    public ResponseEntity<String> spin(@PathVariable String userId) {
        if (historyService.hasClaimedToday(userId, "SPIN")) {
            return ResponseEntity.badRequest().body("❌ Hôm nay bạn đã quay rồi!");
        }

        // Kết quả quay ngẫu nhiên
        String[] prizes = {"NONE", "POINTS", "VOUCHER"};
        String result = prizes[new Random().nextInt(prizes.length)];

        String message;
        switch (result) {
            case "POINTS" -> {
                rewardService.addPoints(userId, 20);
                message = "🎉 Chúc mừng! Bạn nhận được 20 điểm!";
            }
            case "VOUCHER" -> {
                rewardService.grantVoucherAfterSpin(userId, "SALE50");
                message = "🎊 Chúc mừng! Bạn trúng voucher SALE50!";
            }
            default -> message = "😅 Rất tiếc, bạn chưa trúng thưởng lần này.";
        }

        historyService.saveHistory(userId, "SPIN", message);
        log.info("🎡 Kết quả quay thưởng của {}: {}", userId, message);
        return ResponseEntity.ok(message);
    }

    /**
     * 📜 Xem lịch sử mini game
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<MiniGameHistory>> getHistory(@PathVariable String userId) {
        log.info("📜 User {} xem lịch sử minigame", userId);
        return ResponseEntity.ok(historyService.getHistoryByUser(userId));
    }

    /**
     * 📊 Xem tổng kết điểm & voucher thật từ voucher-service
     */
    @GetMapping("/summary/{userId}")
    public ResponseEntity<RewardInfoDTO> getRewardSummary(@PathVariable String userId) {
        log.info("📊 User {} xem tổng điểm & voucher hiện có", userId);
        return ResponseEntity.ok(rewardService.getRewardSummary(userId));
    }
}
