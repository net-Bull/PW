-- Utenti dimostrativi, caricati solo dalla location db/demo: profilo predefinito (H2, sviluppo e test)
-- e profilo "demo". Il profilo "postgres" da solo non la carica: in un'installazione reale gli utenti
-- li crea l'amministratore.
-- Credenziali (solo demo): operatore / Operatore-Demo-2026, operatore2 / Operatore2-Demo-2026,
-- approvatore / Approvatore-Demo-2026, approvatore2 / Approvatore2-Demo-2026.
-- Hash BCrypt (costo 10) con prefisso {bcrypt} per il DelegatingPasswordEncoder di Spring Security.

INSERT INTO utente (username, password_hash, nome_completo, ruolo, attivo)
VALUES
('operatore', '{bcrypt}$2a$10$Tj3VmlpJT2xnmw.KwneSxeljsh4BhHslUBhAxXPGJY1oKQjxnKMFO', 'Operatore SOC (demo)', 'OPERATORE', TRUE),
('operatore2', '{bcrypt}$2a$10$Ac6Wnuz2mR9sgtACe2xhvOK.V0L5UXlCIAd2wpQflSwiX4zleBZ6m', 'Secondo operatore SOC (demo)', 'OPERATORE', TRUE),
('approvatore', '{bcrypt}$2a$10$2gqFRQSm1V25MU1Mh9nXeu0NLT6f.JOFXGMitV4oW/58JQXtcWrzm', 'Responsabile sicurezza (demo)', 'APPROVATORE', TRUE),
('approvatore2', '{bcrypt}$2a$10$GH/vfu5BrU.6SrFPyG9fVetyR2WxZC9q0/sKIlwLT91mau4JHoGYW', 'Vice responsabile sicurezza (demo)', 'APPROVATORE', TRUE)
ON CONFLICT DO NOTHING;
