package it.pw29.notifiche.service;

import it.pw29.notifiche.dto.RigaMatrice;
import org.springframework.stereotype.Service;

import it.pw29.notifiche.domain.TipoAdempimento;

import java.util.List;

/** Esposizione della matrice campo/adempimento verso il livello controller. */
@Service
public class MatriceCampiService {

    private final MatriceCampi matrice;

    public MatriceCampiService(MatriceCampi matrice) {
        this.matrice = matrice;
    }

    public List<RigaMatrice> righe() {
        return matrice.righe();
    }

    public List<MatriceCampi.CampoModulo> campiModulo(TipoAdempimento tipo) {
        return matrice.campiModulo(tipo);
    }

    public String markdown() {
        return matrice.markdown();
    }
}
