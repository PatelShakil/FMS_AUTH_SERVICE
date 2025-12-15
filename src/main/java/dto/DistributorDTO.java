package dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistributorDTO {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String profileUrl;
    private String accountStatus;
    private LocalDateTime appliedAt;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private String rejectionReason;
    private LocalDateTime createdAt;
}
