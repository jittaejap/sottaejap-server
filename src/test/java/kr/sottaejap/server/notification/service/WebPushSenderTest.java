package kr.sottaejap.server.notification.service;

import kr.sottaejap.server.notification.WebPushProperties;
import kr.sottaejap.server.notification.domain.PushSubscription;
import kr.sottaejap.server.notification.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.Base64Encoder;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * build.gradle.kts에서 web-push가 딸고 오는 HTTP 스택 셋을 제외했다. 그래도 암호화(AES128GCM)와
 * VAPID 서명이 끝까지 도는지 — 즉 제외해도 되는 것만 제외했는지 — 여기서 확인한다.
 *
 * <p>실제 전송은 하지 않는다. 요청을 만드는 데까지가 이 클래스의 책임이고, 그 뒤는 JDK HttpClient다.
 */
class WebPushSenderTest {

    /** 브라우저가 실제로 주는 것과 같은 모양의 엔드포인트. FCM 주소는 라이브러리가 레거시 경로로 다시 쓴다. */
    private static final String ENDPOINT = "https://updates.push.services.mozilla.com/wpush/v2/fake-endpoint";

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);

    @Test
    void registersBouncyCastleItself() {
        // provider를 등록하는 것은 라이브러리의 PushService뿐인데 우리는 그 클래스를 쓰지 않는다.
        // WebPushSender를 로드하는 것만으로 "BC"가 준비돼야 한다 — 아니면 기동 시점에 죽는다.
        // 이 파일의 다른 테스트도 provider를 직접 등록하지 않으므로, 빠지면 전부 같이 실패한다.
        new WebPushSender(new WebPushProperties("", "", ""), repository, JsonMapper.builder().build());
        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
    }

    @Test
    void buildsSignedAndEncryptedRequest() throws Exception {
        WebPushSender sender = new WebPushSender(
                properties(generateP256()), repository, JsonMapper.builder().build());
        String payload = "{\"title\":\"소때잡\",\"body\":\"8월 22일 ○○배달 12,000원, 어땠는지 돌아볼까요?\"}";

        HttpRequest request = sender.buildRequest(subscription(generateP256()), payload);

        assertThat(sender.isEnabled()).isTrue();
        assertThat(request.getUrl()).isEqualTo(ENDPOINT);
        // VAPID 서명이 붙었다는 증거. 이 헤더가 없으면 푸시 서비스가 401을 준다.
        assertThat(request.getHeaders().get("Authorization")).startsWith("vapid t=");
        assertThat(request.getHeaders()).containsEntry("Content-Encoding", "aes128gcm");
        assertThat(request.getHeaders()).containsEntry("TTL", "86400");
        // 본문이 암호화됐다 — 평문이 그대로 실려 나가면 안 된다.
        assertThat(new String(request.getBody())).doesNotContain("소때잡");
        assertThat(request.getBody().length).isGreaterThan(payload.getBytes().length);
    }

    @Test
    void staysOffWhenVapidKeysAreMissing() {
        WebPushSender sender = new WebPushSender(
                new WebPushProperties("", "", ""), repository, JsonMapper.builder().build());

        sender.send(1L, "메시지", 1043L);

        assertThat(sender.isEnabled()).isFalse();
        // 꺼져 있으면 구독을 조회하지도 않는다.
        verify(repository, never()).findByUserId(anyLong());
    }

    @Test
    void refusesToStartWithMalformedKeys() {
        // BouncyCastle이 GeneralSecurityException이 아니라 IllegalArgumentException을 던지는 경로다.
        assertThatThrownBy(() -> new WebPushSender(
                new WebPushProperties("not-a-key", "not-a-key", "mailto:team@sottaejap.kr"),
                repository, JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAPID");
    }

    private static WebPushProperties properties(KeyPair vapid) {
        return new WebPushProperties(
                Base64Encoder.encodeUrlWithoutPadding(Utils.encode((ECPublicKey) vapid.getPublic())),
                Base64Encoder.encodeUrlWithoutPadding(Utils.encode((ECPrivateKey) vapid.getPrivate())),
                "mailto:team@sottaejap.kr");
    }

    private static PushSubscription subscription(KeyPair browser) {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return PushSubscription.of(
                1L,
                ENDPOINT,
                Base64Encoder.encodeUrlWithoutPadding(Utils.encode((ECPublicKey) browser.getPublic())),
                Base64Encoder.encodeUrlWithoutPadding(auth),
                OffsetDateTime.now());
    }

    private static KeyPair generateP256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }
}
