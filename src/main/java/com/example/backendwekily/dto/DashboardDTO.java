package com.example.backendwekily.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardDTO {

    private UserDTO user;
    private BigDecimal totalBalance;
    private BigDecimal todayDeposits;
    private BigDecimal todayWithdrawals;
    private Integer transactionCount;
    private List<AgencyDTO> agencies;
    private StatsDTO stats;
    private List<TransactionDTO> recentTransactions;

    // ── Sous-objets ─────────────────────────────────────────

    @Data @Builder
    public static class UserDTO {
        private Long id;
        private String name;
        private String role;
        private String initials;
        private String phone;
    }

    @Data @Builder
    public static class AgencyDTO {
        private Long id;
        private String name;
        private BigDecimal balance;
        private BigDecimal todayChange;
        private String icon;
    }

    @Data @Builder
    public static class StatsDTO {
        private BigDecimal withdrawals;
        private Integer withdrawalCount;
        private BigDecimal deposits;
        private Integer depositCount;
        private Integer pending;
        private BigDecimal delegations;
    }

    @Data @Builder
    public static class TransactionDTO {
        private Long id;
        private String name;
        private BigDecimal amount;
        private String agency;
        private String date;
        private String type;
        private String status;
    }
}