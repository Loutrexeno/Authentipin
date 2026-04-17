package fr.authentipin.data;

import java.util.UUID;

public record VerificationRecord(UUID uuid, String discordId, String pin, boolean verified) {
}
