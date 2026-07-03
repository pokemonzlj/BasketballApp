import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// --- 颜色定义 ---
val BgColor = Color(0xFF1A1A2E)
val CardColor = Color(0xFF1F4068)
val MainColor = Color(0xFFFF6B35)
val WinColor = Color(0xFF4ADE80)
val LoseColor = Color(0xFFF87171)
val GrayColor = Color(0xFF888888)
val LineColor = Color(0xFF4A6FA5)

// --- 数据模型 ---
data class PlayoffMatch(
    var team1: String, var score1: String, var team1Win: Boolean,
    var team2: String, var score2: String, var team2Win: Boolean
)

data class PlayoffRound(
    var title: String,
    var matches: MutableList<PlayoffMatch>
)

// --- 季后赛状态与逻辑管理 ---
class PlayoffManager {
    var rounds by mutableStateOf(mutableListOf<PlayoffRound>())
        private set

    // 记录我方当前系列赛的战绩
    var myWins by mutableStateOf(0)
        private set
    var myLosses by mutableStateOf(0)
        private set

    init {
        initPlayoffs()
    }

    private fun initPlayoffs() {
        rounds.clear()
        myWins = 0
        myLosses = 0
        
        val teamPool = mutableListOf("湖人", "热火", "凯尔特人", "太阳", "掘金", "雄鹿", "76人", "快船")
        val myOpponent = teamPool.random()
        teamPool.remove(myOpponent)
        
        val round1Matches = mutableListOf(
            PlayoffMatch("我方", "0", false, myOpponent, "0", false)
        )
        
        while (teamPool.isNotEmpty()) {
            val t1 = teamPool.random(); teamPool.remove(t1)
            val t2 = teamPool.random(); teamPool.remove(t2)
            round1Matches.add(PlayoffMatch(t1, "0", false, t2, "0", false))
        }
        
        rounds.add(PlayoffRound("东部赛区：8强", round1Matches))
    }

    /**
     * 外部调用：记录我方的一场比赛结果
     * @param isWin 我方这场比赛是否获胜
     */
    fun recordMyGameResult(isWin: Boolean) {
        if (rounds.isEmpty()) return
        
        val currentRound = rounds.last()
        // 如果已经拿到4胜或者已经打到总决赛结束，不再记录
        if (myWins >= 4) return 

        if (isWin) myWins++ else myLosses++

        val myMatch = currentRound.matches.first { it.team1 == "我方" || it.team2 == "我方" }
        if (myMatch.team1 == "我方") {
            myMatch.score1 = myWins.toString()
            myMatch.score2 = myLosses.toString()
        } else {
            myMatch.score1 = myLosses.toString()
            myMatch.score2 = myWins.toString()
        }

        // 如果我方胜场达到4场，触发晋级逻辑
        if (myWins == 4) {
            advanceToNextRound(currentRound)
        }
    }

    // 触发晋级：结算其他比赛并生成下一轮
    private fun advanceToNextRound(currentRound: PlayoffRound) {
        val winners = mutableListOf<String>()

        // 1. 结算当前轮次所有对阵的结果
        currentRound.matches.forEach { match ->
            if (match.team1 == "我方" || match.team2 == "我方") {
                // 我方比赛：标记最终状态
                if (match.team1 == "我方") {
                    match.team1Win = true
                    match.team2Win = false
                } else {
                    match.team2Win = true
                    match.team1Win = false
                }
                winners.add("我方")
            } else {
                // 其他队伍：系统随机判定胜负和比分
                val loserScore = Random.nextInt(4) // 0, 1, 2, 3
                if (Random.nextBoolean()) {
                    match.score1 = "4"
                    match.team1Win = true
                    match.score2 = loserScore.toString()
                    match.team2Win = false
                    winners.add(match.team1)
                } else {
                    match.score2 = "4"
                    match.team2Win = true
                    match.score1 = loserScore.toString()
                    match.team1Win = false
                    winners.add(match.team2)
                }
            }
        }

        // 2. 生成下一轮对阵
        val nextRoundTitle = when (currentRound.title) {
            "东部赛区：8强" -> "东部赛区：4强"
            "东部赛区：4强" -> "东部赛区：决赛"
            "东部赛区：决赛" -> "总决赛"
            else -> ""
        }

        if (nextRoundTitle.isEmpty()) return // 总决赛打完

        val nextMatches = mutableListOf<PlayoffMatch>()
        if (nextRoundTitle == "总决赛") {
            nextMatches.add(PlayoffMatch("我方", "0", false, "西部冠军", "0", false))
        } else {
            for (i in winners.indices step 2) {
                if (i + 1 < winners.size) {
                    nextMatches.add(PlayoffMatch(winners[i], "0", false, winners[i + 1], "0", false))
                }
            }
        }

        // 重置我方系列赛战绩，准备下一轮
        myWins = 0
        myLosses = 0
        rounds.add(PlayoffRound(nextRoundTitle, nextMatches))
    }
}

@Composable
fun PlayoffScreen(playoffManager: PlayoffManager) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // 1. 顶部标题
        PlayoffHeader()

        // 2. 对阵图 (支持横向滚动)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                playoffManager.rounds.forEachIndexed { index, round ->
                    PlayoffRoundColumn(
                        round = round,
                        isFirstRound = index == 0,
                        isLastRound = index == playoffManager.rounds.size - 1
                    )
                }
            }
        }

        // 3. 底部信息
        val currentOpponent = playoffManager.rounds.lastOrNull()?.matches?.firstOrNull { it.team1 == "我方" || it.team2 == "我方" }?.let {
            if (it.team1 == "我方") it.team2 else it.team1
        } ?: "待定"

        PlayoffBottomInfo(
            myWins = playoffManager.myWins,
            myLosses = playoffManager.myLosses,
            currentOpponent = currentOpponent
        )
    }
}

@Composable
fun PlayoffHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        Text("🎯 季后赛", color = MainColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("S1 2026 · 季后赛阶段", color = GrayColor, fontSize = 16.sp)
    }
    Divider(color = Color(0xFF2A2A4E), thickness = 1.dp)
}

@Composable
fun PlayoffRoundColumn(round: PlayoffRound, isFirstRound: Boolean, isLastRound: Boolean) {
    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            round.title,
            color = MainColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        val pairs = round.matches.chunked(2)
        pairs.forEach { pair ->
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                pair.forEach { match ->
                    MatchCard(
                        match = match,
                        isFirstRound = isFirstRound,
                        isLastRound = isLastRound
                    )
                }
            }
        }
    }
}

@Composable
fun MatchCard(match: PlayoffMatch, isFirstRound: Boolean, isLastRound: Boolean) {
    Box(
        modifier = Modifier.padding(vertical = 10.dp)
    ) {
        if (!isFirstRound) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(20.dp)
                    .height(1.dp)
                    .offset(x = (-20).dp)
                    .background(LineColor)
            )
        }
        if (!isLastRound) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(20.dp)
                    .height(1.dp)
                    .offset(x = 20.dp)
                    .background(LineColor)
            )
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                TeamRow(match.team1, match.score1, match.team1Win)
                TeamRow(match.team2, match.score2, match.team2Win)
            }
        }
    }
}

@Composable
fun TeamRow(teamName: String, score: String, isWin: Boolean) {
    val color = when {
        isWin -> WinColor
        score != "0" -> LoseColor
        else -> GrayColor
    }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            teamName,
            color = color,
            fontWeight = if (isWin) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
        Text(
            score,
            color = color,
            fontWeight = if (isWin) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PlayoffBottomInfo(myWins: Int, myLosses: Int, currentOpponent: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(WinColor, "获胜")
            Spacer(modifier = Modifier.width(20.dp))
            LegendItem(LoseColor, "失败")
            Spacer(modifier = Modifier.width(20.dp))
            LegendItem(GrayColor, "待比赛")
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MainColor, RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🏆 当前对阵：我方 vs $currentOpponent",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$myWins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(" 胜 - ", color = GrayColor, fontSize = 14.sp)
                    Text("$myLosses", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(" 负 · 7局4胜", color = GrayColor, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, color = GrayColor, fontSize = 12.sp)
    }
}
