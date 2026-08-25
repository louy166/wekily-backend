package com.example.backendwekily.controller;

import com.example.backendwekily.entity.TauxChangeEntity;
import com.example.backendwekily.repository.TauxChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/taux-change")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TauxChangeController {

    private final TauxChangeRepository tauxChangeRepository;

    // ✅ GET /api/taux-change — retourne tous les taux
    // Champs retournés : id, devEnv, devRet, tau (noms exacts de TauxChangeEntity)
    @GetMapping
    public ResponseEntity<List<TauxChangeEntity>> getAllTaux() {
        return ResponseEntity.ok(tauxChangeRepository.findAll());
    }

    // POST /api/taux-change — ajouter un nouveau taux (admin)
    @PostMapping
    public ResponseEntity<TauxChangeEntity> addTaux(@RequestBody TauxChangeEntity taux) {
        return ResponseEntity.ok(tauxChangeRepository.save(taux));
    }

    // DELETE /api/taux-change/{id} — supprimer un taux (admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTaux(@PathVariable Long id) {
        tauxChangeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}