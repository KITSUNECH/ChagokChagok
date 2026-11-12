// 차곡車곡 – 일기 문장을 생성형 AI로 다듬는 곳
// 사실은 Writer 가 차량 기록으로 이미 써 두었다. 여기서는 그 초안을 더 자연스러운 글로만 바꾼다.
// 차 밖으로 나가는 것은 초안 글 한 덩어리뿐이고, 위치나 원본 신호는 보내지 않는다.
package com.example.chagokchagok

import android.os.Handler
import android.os.Looper
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.util.concurrent.Executors

object AiDiary {
    /** 키는 local.properties 에서 빌드 때 들어온다. 비어 있으면 AI를 쓰지 않는다. */
    val enabled get() = BuildConfig.ANTHROPIC_API_KEY.isNotBlank()

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder().apiKey(BuildConfig.ANTHROPIC_API_KEY).build()
    }

    private const val SYSTEM = """너는 '차곡車곡'이라는 차량용 앱에서 일기를 쓴다. 글쓴이는 자동차이고, 읽는 사람은 그 차를 모는 초보 운전자다. 운전이 무서워서 시작을 망설이던 사람이라, 이 일기는 평가가 아니라 "오늘 이런 일이 있었고 네가 늘고 있다"는 기록이어야 한다.

사용자가 주는 초안은 차량 신호로 확인된 사실만 담고 있다. 이 초안을 차가 운전자에게 건네는 다정한 일기로 다시 써라.

지킬 것:
- 초안에 있는 사실만 쓴다. 횟수, 날씨, 사람, 장소를 새로 만들지 않는다. 초안에 없는 감정이나 사건을 단정하지 않는다. 일기가 사실과 다르면 운전자가 차를 믿지 못하게 된다.
- 초안의 숫자와 횟수는 값을 바꾸지 않는다.
- 점수를 매기거나 잘잘못을 따지지 않는다. 잘한 것과 나아진 것을 먼저 쓰고, 다음에 챙길 것은 초안에 있을 때만 맨 끝에 한 문장으로 쓴다.
- 문체는 "~했다", "~더라" 같은 일기체 반말이다. 운전자는 "너"라고 부른다.
- 세 문장에서 다섯 문장, 공백을 포함해 160자 안쪽으로 쓴다. 차 화면의 원고지에 들어가는 글이라 길면 넘친다.
- 일기 본문만 한 줄로 출력한다. 제목, 따옴표, 설명, 줄바꿈을 붙이지 않는다."""

    /**
     * 초안을 다듬어 돌려준다. 결과는 화면 스레드에서 전달된다.
     * @param done (다듬은 글 또는 null, 점검 창에 보여 줄 상태 문구)
     */
    fun polish(d: Diary, guide: Guide?, done: (String?, String) -> Unit) {
        pool.execute {
            val (text, status) = try {
                val keep = if (d.highlight.isNotEmpty()) "\n\n이 문장은 글자 그대로 넣어라: ${d.highlight}" else ""
                val context = if (guide != null) "\n\n상황: ${Writer.subj(guide.owner)} 남긴 길 '${guide.title}'을 따라 달린 날이다." else ""
                val params = MessageCreateParams.builder()
                    .model("claude-opus-5-5")
                    .maxTokens(16000L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .system(SYSTEM)
                    .addUserMessage("초안:\n${d.text}$context$keep")
                    .build()
                val response = client.messages().create(params)
                // 응답이 거절이거나 비어 있으면 규칙으로 쓴 글을 그대로 쓴다.
                if (response.stopReason().orElse(null) == StopReason.REFUSAL) null to "거절됨 (규칙으로 씀)"
                else {
                    val out = StringBuilder()
                    response.content().forEach { block -> block.text().ifPresent { out.append(it.text()) } }
                    val clean = out.toString().replace("\n", " ").trim().trim('"', '“', '”')
                    if (clean.length in 20..240) clean to "다듬음" else null to "글 길이가 맞지 않음 (규칙으로 씀)"
                }
            } catch (e: RateLimitException) {
                null to "요청 한도 초과 (규칙으로 씀)"
            } catch (e: AnthropicServiceException) {
                null to "API 오류 ${e.statusCode()} (규칙으로 씀)"
            } catch (e: Throwable) {
                // 이 기기에서 SDK가 동작하지 않는 경우까지 포함해, 어떤 실패든 앱은 계속 돌아가야 한다.
                null to "연결 실패: ${e.javaClass.simpleName} (규칙으로 씀)"
            }
            main.post { done(text, status) }
        }
    }
}
