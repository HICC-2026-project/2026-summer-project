package com.career.recommendation.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NicknameRequest {

    /** users.nickname은 VARCHAR(50). 제어 문자·줄바꿈은 막고, 앞뒤 공백은 서비스에서 제거한다. */
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    @Pattern(regexp = "^[^\\p{Cntrl}]+$", message = "닉네임에 제어 문자를 쓸 수 없습니다.")
    private String nickname;
}
