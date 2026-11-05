package it.pw29.notifiche.dto;

import it.pw29.notifiche.domain.StatoAdempimento;
import it.pw29.notifiche.domain.TipoAdempimento;

import java.time.Instant;
import java.util.List;

public record AdempimentoDto(Long id, TipoAdempimento tipo, int progressivo, StatoAdempimento stato,
                             Instant richiestoIl, Instant scadenzaRichiesta, String riferimentoRichiesta,
                             Instant trasmessoIl, List<VersioneSintesi> versioni) {
}
