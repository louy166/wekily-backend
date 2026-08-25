package com.example.backendwekily;

import com.example.backendwekily.entity.Bank;
import com.example.backendwekily.entity.DepositCommissionTier;
import com.example.backendwekily.entity.WithdrawalFeeTier;
import com.example.backendwekily.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final BankRepository bankRepository;
    private final EntityManager  em;

    // ── Données des banques ───────────────────────────────────
    private static final String[][] BANKS = {
            {"بنكيلي",   "bankily"},
            {"مصرفي",    "masrvi" },
            {"السداد",   "sedad"  },
            {"موف موني", "moov"   },
            {"بيم بنك",  "bimbank"},
            {"أمانتي",   "amanty" },
            {"كليك",     "click"  },
    };

    // ── Tranches retrait ──────────────────────────────────────
    private static final double[][] WITHDRAWAL_TIERS = {
            {10,    500,   10 },
            {501,   1000,  20 },
            {1001,  2000,  40 },
            {2001,  5000,  70 },
            {5001,  10000, 120},
            {10001, 15000, 170},
            {15001, 20000, 220},
    };

    // ── Tranches dépôt ────────────────────────────────────────
    private static final double[][] DEPOSIT_TIERS = {
            {10,    500,   3 },
            {501,   1000,  6 },
            {1001,  2000,  12},
            {2001,  5000,  25},
            {5001,  10000, 50},
            {10001, 15000, 70},
            {15001, 20000, 90},
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("🚀 DataInitializer — démarrage...");
        long bankCount = bankRepository.count();

        if (bankCount >= 7) {
            log.info("✅ Données déjà initialisées ({} banques)", bankCount);
            return;
        }

        log.info("🔧 Initialisation des banques et tranches...");

        for (String[] b : BANKS) {
            // Créer la banque si elle n'existe pas
            Bank bank = bankRepository.findByIcon(b[1]).orElseGet(() -> {
                Bank newBank = Bank.builder().name(b[0]).icon(b[1]).build();
                return bankRepository.save(newBank);
            });

            // Vérifier si les tranches existent déjà
            Long witCount = (Long) em.createQuery(
                            "SELECT COUNT(t) FROM WithdrawalFeeTier t WHERE t.bank.id = :id")
                    .setParameter("id", bank.getId()).getSingleResult();

            if (witCount == 0) {
                for (double[] t : WITHDRAWAL_TIERS) {
                    WithdrawalFeeTier tier = WithdrawalFeeTier.builder()
                            .bank(bank)
                            .amountFrom(BigDecimal.valueOf(t[0]))
                            .amountTo(BigDecimal.valueOf(t[1]))
                            .fee(BigDecimal.valueOf(t[2]))
                            .build();
                    em.persist(tier);
                }
            }

            Long depCount = (Long) em.createQuery(
                            "SELECT COUNT(t) FROM DepositCommissionTier t WHERE t.bank.id = :id")
                    .setParameter("id", bank.getId()).getSingleResult();

            if (depCount == 0) {
                for (double[] t : DEPOSIT_TIERS) {
                    DepositCommissionTier tier = DepositCommissionTier.builder()
                            .bank(bank)
                            .amountFrom(BigDecimal.valueOf(t[0]))
                            .amountTo(BigDecimal.valueOf(t[1]))
                            .commission(BigDecimal.valueOf(t[2]))
                            .build();
                    em.persist(tier);
                }
            }

            log.info("✅ Banque {} initialisée", bank.getName());
        }

        log.info("✅ DataInitializer terminé — {} banques × 7 tranches", BANKS.length);
    }
}