// 차곡車곡 – 예시 길의 경로
// 공개 도로 경로(OSRM)로 만든 판교 → 양재 쪽 11km 길. 저장소의 tools/예시길.gpx 와 같은 길이라,
// 에뮬레이터에서 그 GPX를 재생하면 예시 길의 한마디가 실제 그 자리에서 나온다.
package com.example.chagokchagok

object SampleRoute {
    private const val RAW = "37.41171,127.09518,37.40994,127.09473,37.40868,127.09318,37.40944,127.09124,37.41487,127.08560,37.42543,127.07495,37.42849,127.07186,37.43068,127.06961,37.43290,127.06740,37.43493,127.06537,37.43654,127.06375,37.43814,127.06216,37.44061,127.06019,37.44302,127.05845,37.44492,127.05708,37.44681,127.05550,37.44838,127.05410,37.45053,127.05216,37.45457,127.04859,37.45761,127.04565,37.45907,127.04424,37.46055,127.04291,37.46209,127.04170,37.46407,127.04172,37.46563,127.04330,37.46737,127.04142,37.46898,127.03972,37.47081,127.03885,37.47274,127.03876,37.47522,127.03878,37.47718,127.03878,37.47899,127.03863,37.48073,127.03790,37.48247,127.03635,37.48384,127.03488,37.48550,127.03367,37.48557,127.03616,37.48542,127.03621"
    /** 위도, 경도 순서의 점들 */
    val points: List<Pair<Double, Double>> by lazy { RAW.split(",").chunked(2).map { it[0].toDouble() to it[1].toDouble() } }
}

/** 경로를 다루는 계산 */
object Paths {
    /** 좌표 목록에 진행 정도(누적 거리 비율)를 붙인다 */
    fun of(points: List<Pair<Double, Double>>): List<Pt> {
        if (points.isEmpty()) return emptyList()
        val acc = DoubleArray(points.size)
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Places.meters(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        val total = acc.last().coerceAtLeast(1.0)
        return points.mapIndexed { i, (la, ln) -> Pt((acc[i] / total).toFloat(), la, ln) }
    }
    fun lengthKm(path: List<Pt>) = path.zipWithNext().sumOf { (a, b) -> Places.meters(a.lat, a.lng, b.lat, b.lng) }.toFloat() / 1000f
    /** 진행 정도 p 에 가장 가까운 점 */
    fun at(path: List<Pt>, p: Float): Pt? = path.minByOrNull { Math.abs(it.p - p) }

    /** 경로에서 (lat,lng)에 가장 가까운 점부터 거꾸로 meters 만큼 앞선 점. 경로가 없으면 null */
    fun before(path: List<Pt>, lat: Double, lng: Double, meters: Int): Pt? {
        if (path.size < 2) return null
        var i = path.indices.minByOrNull { Places.meters(path[it].lat, path[it].lng, lat, lng) } ?: return null
        var acc = 0.0
        while (i > 0 && acc < meters) { acc += Places.meters(path[i].lat, path[i].lng, path[i - 1].lat, path[i - 1].lng); i-- }
        return path[i]
    }
    /** 남에게 건넬 때: 출발한 곳(집일 수 있다) 근처 500m는 뺀다 */
    fun shareable(path: List<Pt>): List<Pt> {
        val first = path.firstOrNull() ?: return path
        return path.filter { Places.meters(first.lat, first.lng, it.lat, it.lng) > 500 }
    }
}
