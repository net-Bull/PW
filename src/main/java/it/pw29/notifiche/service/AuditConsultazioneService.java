package it.pw29.notifiche.service;

import it.pw29.notifiche.domain.Adempimento;
import it.pw29.notifiche.domain.Incidente;
import it.pw29.notifiche.domain.VersioneDocumento;
import it.pw29.notifiche.dto.AuditVoce;
import it.pw29.notifiche.repository.AuditLogRepository;
import it.pw29.notifiche.repository.IncidenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Consultazione del registro di audit di un incidente e dei suoi documenti. */
@Service
public class AuditConsultazioneService {

    private final AuditLogRepository audit;
    private final IncidenteRepository incidenti;

    public AuditConsultazioneService(AuditLogRepository audit, IncidenteRepository incidenti) {
        this.audit = audit;
        this.incidenti = incidenti;
    }

    @Transactional(readOnly = true)
    public List<AuditVoce> perIncidente(Long id) {
        Incidente inc = incidenti.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Incidente", id));
        List<AuditVoce> voci = new ArrayList<>();
        audit.findByEntitaAndEntitaIdOrderByIdAsc("Incidente", id).forEach(a -> voci.add(Mappatura.audit(a)));
        for (Adempimento a : inc.getAdempimenti()) {
            for (VersioneDocumento v : a.getVersioni()) {
                audit.findByEntitaAndEntitaIdOrderByIdAsc("VersioneDocumento", v.getId())
                        .forEach(l -> voci.add(Mappatura.audit(l)));
            }
        }
        voci.sort(Comparator.comparing(AuditVoce::id));
        return voci;
    }
}
