package com.htv.farmverse.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RecoveryCodeServiceTest {

    private final RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
    private final RecoveryCodeService service =
            new RecoveryCodeService(repository, new BCryptPasswordEncoder(4), Clock.systemUTC());

    @Test
    void normalizeAcceptsLowerCaseDashAndSpacesButRejectsBadShapes() {
        assertThat(RecoveryCodeService.normalize("abcde-fghjk")).isEqualTo("ABCDEFGHJK");
        assertThat(RecoveryCodeService.normalize(" ABCDE FGHJK ")).isEqualTo("ABCDEFGHJK");
        assertThat(RecoveryCodeService.normalize("ABCDE-FGHJ")).isNull();      // thiếu ký tự
        assertThat(RecoveryCodeService.normalize("ABCDE-FGHJO")).isNull();     // O không có trong bảng ký tự
        assertThat(RecoveryCodeService.normalize(null)).isNull();
    }

    @Test
    void generateReturnsTenFormattedCodesAndStoresOnlyHashes() {
        List<String> shown = service.generate("user-1");

        ArgumentCaptor<RecoveryCode> saved = ArgumentCaptor.forClass(RecoveryCode.class);
        verify(repository, org.mockito.Mockito.times(10)).save(saved.capture());
        assertThat(shown).hasSize(10).allMatch(code -> code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(shown).doesNotHaveDuplicates();
        for (int i = 0; i < 10; i++) {
            assertThat(saved.getAllValues().get(i).getCodeHash()).doesNotContain(shown.get(i).replace("-", ""));
        }
        verify(repository).deleteByUserId("user-1");
    }

    @Test
    void aCodeWorksOnceThenIsRefused() {
        List<String> shown = service.generate("user-1");
        ArgumentCaptor<RecoveryCode> saved = ArgumentCaptor.forClass(RecoveryCode.class);
        verify(repository, org.mockito.Mockito.times(10)).save(saved.capture());
        List<RecoveryCode> unused = new ArrayList<>(saved.getAllValues());
        when(repository.findByUserIdAndUsedAtIsNull("user-1")).thenAnswer(call ->
                unused.stream().filter(code -> code.getUsedAt() == null).toList());

        assertThat(service.consume("user-1", shown.get(3).toLowerCase())).isTrue();
        assertThat(service.consume("user-1", shown.get(3))).isFalse();
        assertThat(service.consume("user-1", shown.get(4))).isTrue();
        assertThat(service.consume("user-1", "AAAAA-AAAAA")).isFalse();
    }
}
