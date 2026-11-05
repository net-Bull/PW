package it.pw29.notifiche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespingimentoRequest(@NotBlank @Size(max = 2000) String nota) {
}
