package it.pw29.notifiche.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Versione della Tassonomia Cyber ACN caricata, con i dati che ne provano la provenienza:
 * versione del documento ufficiale, data di acquisizione, URL e impronta SHA-256 del PDF.
 */
@Entity
@Table(name = "tassonomia_versione")
public class TassonomiaVersione {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String codice;

    @Column(name = "versione_documento", nullable = false, length = 20)
    private String versioneDocumento;

    @Column(name = "data_acquisizione", nullable = false)
    private LocalDate dataAcquisizione;

    @Column(name = "fonte_url", nullable = false, length = 500)
    private String fonteUrl;

    @Column(name = "fonte_strutturata", nullable = false, length = 500)
    private String fonteStrutturata;

    @Column(name = "sha256_documento", nullable = false, length = 64)
    private String sha256Documento;

    @Column(nullable = false)
    private boolean vigente;

    protected TassonomiaVersione() {
    }

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public String getVersioneDocumento() { return versioneDocumento; }
    public LocalDate getDataAcquisizione() { return dataAcquisizione; }
    public String getFonteUrl() { return fonteUrl; }
    public String getFonteStrutturata() { return fonteStrutturata; }
    public String getSha256Documento() { return sha256Documento; }
    public boolean isVigente() { return vigente; }
}
