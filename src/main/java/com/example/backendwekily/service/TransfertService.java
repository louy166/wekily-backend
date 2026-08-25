package com.example.backendwekily.service;

import com.example.backendwekily.dto.TransfertRequest;
import com.example.backendwekily.dto.TransfertResponse;
import com.example.backendwekily.entity.Agent;
import com.example.backendwekily.entity.TauxChangeEntity;
import com.example.backendwekily.entity.TransfertEntity;
import com.example.backendwekily.repository.AgentRepository;
import com.example.backendwekily.repository.TauxChangeRepository;
import com.example.backendwekily.repository.TransfertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import important

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class TransfertService {

    private final TransfertRepository transfertRepository;
    private final TauxChangeRepository tauxChangeRepository;
    private final AgentRepository agentRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional // Assure que toutes les modifications (transfert + mise à jour solde) s'exécutent ensemble
    public TransfertResponse creerTransfert(TransfertRequest req) {

        // 1. Récupérer l'agent émetteur pour vérifier et déduire son solde
        Agent agentEmetteur = agentRepository.findById(req.getIdAgentEmetteur())
                .orElseThrow(() -> new IllegalArgumentException("لم يتم العثور على الوكيل المرسل"));

        // (Optionnel mais recommandé) Vérifier si l'agent a assez d'argent dans sa caisse
        // Supposons que le champ du solde s'appelle "solde" ou "balance" dans votre entité Agent
        /*
        if (agentEmetteur.getSolde() < req.getMontantEnvoye()) {
            throw new IllegalArgumentException("رصيد الخزينة غير كافٍ لإتمام التحويل");
        }
        // Déduire le montant
        agentEmetteur.setSolde(agentEmetteur.getSolde() - req.getMontantEnvoye());
        agentRepository.save(agentEmetteur);
        */

        // 2. Recherche de l'agent récepteur par téléphone
        Agent agentRecepteur = agentRepository.findByPhone(req.getTelephoneRecepteur().trim())
                .orElseThrow(() -> new IllegalArgumentException("لم يتم العثور على أي وكيل بهذا الرقم: " + req.getTelephoneRecepteur()));

        // 3. Récupération du taux de change
        TauxChangeEntity tauxEntity = tauxChangeRepository
                .findByDevEnvAndDevRet(req.getDevEnv(), req.getDevRet())
                .orElseThrow(() -> new IllegalArgumentException("لا توجد أسعار صرف متوفرة للتحويل من " + req.getDevEnv() + " إلى " + req.getDevRet()));

        // 4. Calcul du montant retiré
        double facteur = parseTauxFactor(tauxEntity.getTau());
        double montantRetire = req.getMontantEnvoye() * facteur;

        // 5. Sauvegarde de la transaction (la référence se gérera toute seule via @PrePersist)
        TransfertEntity transfert = TransfertEntity.builder()
                .idAgentEmetteur(req.getIdAgentEmetteur())
                .idAgentRecepteur(agentRecepteur.getId())
                .montantEnvoye(req.getMontantEnvoye())
                .devEnv(req.getDevEnv())
                .montantRetire(montantRetire)
                .devRet(req.getDevRet())
                .tauApplique(tauxEntity.getTau())
                .statut("CONFIRME")
                .build();

        TransfertEntity saved = transfertRepository.save(transfert);

        // 6. Retour de la réponse
        return TransfertResponse.builder()
                .id(saved.getId())
                .reference(saved.getReference())
                .idAgentEmetteur(saved.getIdAgentEmetteur())
                .idAgentRecepteur(saved.getIdAgentRecepteur())
                .montantEnvoye(saved.getMontantEnvoye())
                .devEnv(saved.getDevEnv())
                .montantRetire(saved.getMontantRetire())
                .devRet(saved.getDevRet())
                .tauApplique(saved.getTauApplique())
                .statut(saved.getStatut())
                .date(saved.getCreatedAt() != null ? saved.getCreatedAt().format(FMT) : "")
                .message("تم إتمام التحويل بنجاح")
                .build();
    }

    private double parseTauxFactor(String tauStr) {
        if (tauStr.contains("/")) {
            String[] parts = tauStr.split("/");
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }
        return Double.parseDouble(tauStr);
    }
}