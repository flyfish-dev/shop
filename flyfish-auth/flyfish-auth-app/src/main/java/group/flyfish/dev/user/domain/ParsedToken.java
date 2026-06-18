package group.flyfish.dev.user.domain;

import java.util.Date;

public record ParsedToken(String subject, String id, Date expiration) {
}
