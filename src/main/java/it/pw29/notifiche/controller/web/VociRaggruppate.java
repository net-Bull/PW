package it.pw29.notifiche.controller.web;

import it.pw29.notifiche.service.TassonomiaService.ValoreSelezionabile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raggruppa i valori della tassonomia per macrocategoria e predicato, nell'ordine dell'albero, per il selettore
 * delle voci ({@code @vociRaggruppate} nei template).
 */
@Component("vociRaggruppate")
public class VociRaggruppate {

    public record Predicato(String nome, List<ValoreSelezionabile> valori) {
    }

    public record Gruppo(String macrocategoria, List<Predicato> predicati, List<ValoreSelezionabile> valori) {
        /** Vero se il gruppo contiene almeno una voce scelta: il gruppo si apre già espanso. */
        public boolean contiene(Collection<String> codici) {
            return codici != null && valori.stream().anyMatch(v -> codici.contains(v.codice()));
        }
    }

    public List<Gruppo> raggruppa(List<ValoreSelezionabile> valori) {
        Map<String, Map<String, List<ValoreSelezionabile>>> albero = new LinkedHashMap<>();
        for (ValoreSelezionabile v : valori) {
            albero.computeIfAbsent(v.macrocategoria(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(v.predicato(), k -> new ArrayList<>()).add(v);
        }
        List<Gruppo> gruppi = new ArrayList<>();
        albero.forEach((macro, predicati) -> {
            List<Predicato> p = new ArrayList<>();
            List<ValoreSelezionabile> tutti = new ArrayList<>();
            predicati.forEach((nome, vv) -> {
                p.add(new Predicato(nome, vv));
                tutti.addAll(vv);
            });
            gruppi.add(new Gruppo(macro, p, tutti));
        });
        return gruppi;
    }
}
