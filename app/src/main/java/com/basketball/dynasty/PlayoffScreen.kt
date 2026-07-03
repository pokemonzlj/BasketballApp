package com.basketball.dynasty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.content.SharedPreferences

// --- 季后赛数据与逻辑管理 ---
data class PlayoffMatch(
    var team1: String, var score1: String, var team1Win: Boolean,
    var team2: String, var score2: String, var team2Win: Boolean
)

data class PlayoffRound(var title: String, var matches: MutableList<PlayoffMatch>)

class PlayoffManager {
    var rounds by mutableStateOf(mutableListOf<PlayoffRound>())
        private set
    var myWins by mutableStateOf(0)
        private set
    var myLosses by mutableStateOf(0)
        private set
    var isPlayoffActive by mutableStateOf(false)
        private set
    // 季后赛系列赛已结束（赢下总冠军或输掉某轮），需触发新赛季
    var playoffEnded by mutableStateOf(false)
        private set
    var isChampion by mutableStateOf(false)
        private set
    var eliminatedRoundTitle by mutableStateOf("")
        private set

    // 标记季后赛结束：保留对阵图供查看，但隐藏"当前对阵"版块
    private fun markEnded(champion: Boolean, roundTitle: String) {
        playoffEnded = true
        isChampion = champion
        eliminatedRoundTitle = roundTitle
        isPlayoffActive = false
    }

    // 新赛季开始后清除结束标记
    fun clearEndedState() {
        playoffEnded = false
        isChampion = false
        eliminatedRoundTitle = ""
    }

    // 保存到 SharedPreferences
    fun saveToPrefs(prefs: SharedPreferences) {
        prefs.edit().apply {
            putString("playoff_rounds", rounds.joinToString(";") {
                "${it.title}:${it.matches.joinToString("|") {
                    "${it.team1},${it.score1},${it.team1Win},${it.team2},${it.score2},${it.team2Win}"
                }}"
            })
            putInt("playoff_my_wins", myWins)
            putInt("playoff_my_losses", myLosses)
            putBoolean("is_playoff_active", isPlayoffActive)
            putBoolean("playoff_ended", playoffEnded)
            putBoolean("playoff_is_champion", isChampion)
            putString("playoff_eliminated_round", eliminatedRoundTitle)
        }.apply()
    }

    // 从 SharedPreferences 恢复
    fun loadFromPrefs(prefs: SharedPreferences) {
        val roundsData = prefs.getString("playoff_rounds", "")
        if (roundsData.isNullOrEmpty()) return

        val roundsList = mutableListOf<PlayoffRound>()
        roundsData.split(";").forEach { roundStr ->
            val parts = roundStr.split(":")
            if (parts.size == 2) {
                val title = parts[0]
                val matchesData = parts[1]
                val matches = mutableListOf<PlayoffMatch>()
                matchesData.split("|").forEach { matchStr ->
                    val matchParts = matchStr.split(",")
                    if (matchParts.size == 6) {
                        matches.add(PlayoffMatch(
                            team1 = matchParts[0],
                            score1 = matchParts[1],
                            team1Win = matchParts[2].toBoolean(),
                            team2 = matchParts[3],
                            score2 = matchParts[4],
                            team2Win = matchParts[5].toBoolean()
                        ))
                    }
                }
                roundsList.add(PlayoffRound(title, matches))
            }
        }
        rounds = roundsList
        myWins = prefs.getInt("playoff_my_wins", 0)
        myLosses = prefs.getInt("playoff_my_losses", 0)
        isPlayoffActive = prefs.getBoolean("is_playoff_active", false)
        playoffEnded = prefs.getBoolean("playoff_ended", false)
        isChampion = prefs.getBoolean("playoff_is_champion", false)
        eliminatedRoundTitle = prefs.getString("playoff_eliminated_round", "") ?: ""
    }

    // 用数据库记录修正我方胜负和当前对阵状态（不重建对阵图，保留本地其他队伍模拟数据）
    // playoffStats: opponent -> [wins, losses]
    fun syncMyStatsFromDB(playoffStats: Map<String, IntArray>) {
        if (playoffStats.isEmpty()) return

        // 找到当前对阵：双方都未达到4胜的对手
        var currentOpponent = ""
        var currentMyWins = 0
        var currentMyLosses = 0

        for ((opp, stats) in playoffStats) {
            val myW = stats[0]
            val myL = stats[1]
            if (myW < 4 && myL < 4) {
                // 未完成的系列赛 = 当前对阵
                currentOpponent = opp
                currentMyWins = myW
                currentMyLosses = myL
            }
        }

        if (currentOpponent.isNotEmpty()) {
            // 季后赛仍在进行中，修正我方胜负
            isPlayoffActive = true
            myWins = currentMyWins
            myLosses = currentMyLosses

            // 更新当前对阵中的比分（本地对阵图已加载，只需同步比分）
            val currentRound = rounds.lastOrNull()
            if (currentRound != null) {
                val myMatch = currentRound.matches.firstOrNull { it.team1 == "我方" || it.team2 == "我方" }
                if (myMatch != null) {
                    if (myMatch.team1 == "我方") {
                        myMatch.score1 = currentMyWins.toString()
                        myMatch.score2 = currentMyLosses.toString()
                    } else {
                        myMatch.score1 = currentMyLosses.toString()
                        myMatch.score2 = currentMyWins.toString()
                    }
                }
            }
        }
    }

    fun startPlayoffs() {
        rounds.clear()
        myWins = 0
        myLosses = 0
        isPlayoffActive = true
        clearEndedState()
        
        val teamPool = allOpponents.shuffled().take(7).toMutableList()
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

    fun getCurrentOpponent(): String {
        val currentRound = rounds.lastOrNull() ?: return ""
        val myMatch = currentRound.matches.firstOrNull { it.team1 == "我方" || it.team2 == "我方" } ?: return ""
        return if (myMatch.team1 == "我方") myMatch.team2 else myMatch.team1
    }

    fun recordMyGameResult(isWin: Boolean) {
        if (rounds.isEmpty()) return
        if (playoffEnded) return
        val currentRound = rounds.last()
        if (myWins >= 4 || myLosses >= 4) return

        if (isWin) myWins++ else myLosses++

        val myMatch = currentRound.matches.first { it.team1 == "我方" || it.team2 == "我方" }
        if (myMatch.team1 == "我方") {
            myMatch.score1 = myWins.toString()
            myMatch.score2 = myLosses.toString()
        } else {
            myMatch.score1 = myLosses.toString()
            myMatch.score2 = myWins.toString()
        }

        if (myWins == 4) {
            // 我方赢下系列赛
            if (myMatch.team1 == "我方") { myMatch.team1Win = true; myMatch.team2Win = false }
            else { myMatch.team2Win = true; myMatch.team1Win = false }

            if (currentRound.title == "总决赛") {
                // 赢下总冠军
                markEnded(champion = true, roundTitle = "")
            } else {
                advanceToNextRound(currentRound)
            }
        } else if (myLosses == 4) {
            // 我方输掉系列赛
            if (myMatch.team1 == "我方") { myMatch.team1Win = false; myMatch.team2Win = true }
            else { myMatch.team2Win = false; myMatch.team1Win = true }
            markEnded(champion = false, roundTitle = currentRound.title)
        }
    }

    private fun advanceToNextRound(currentRound: PlayoffRound) {
        val winners = mutableListOf<String>()
        currentRound.matches.forEach { match ->
            if (match.team1 == "我方" || match.team2 == "我方") {
                // 我方胜负已在 recordMyGameResult 中标记
                winners.add("我方")
            } else {
                val loserScore = Random.nextInt(4)
                if (Random.nextBoolean()) {
                    match.score1 = "4"; match.team1Win = true
                    match.score2 = loserScore.toString(); match.team2Win = false
                    winners.add(match.team1)
                } else {
                    match.score2 = "4"; match.team2Win = true
                    match.score1 = loserScore.toString(); match.team1Win = false
                    winners.add(match.team2)
                }
            }
        }

        val nextRoundTitle = when (currentRound.title) {
            "东部赛区：8强" -> "东部赛区：4强"
            "东部赛区：4强" -> "东部赛区：决赛"
            "东部赛区：决赛" -> "总决赛"
            else -> ""
        }

        if (nextRoundTitle.isEmpty()) {
            // 保险分支：正常流程下总决赛胜利已在 recordMyGameResult 处理
            markEnded(champion = true, roundTitle = "")
            return
        }

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

        myWins = 0
        myLosses = 0
        rounds.add(PlayoffRound(nextRoundTitle, nextMatches))
    }

    // 结束季后赛并重置赛季数据
    fun endPlayoffs() {
        isPlayoffActive = false
        rounds.clear()
        myWins = 0
        myLosses = 0
    }
}

// ================= 季后赛 UI =================
@Composable
fun PlayoffScreen(playoffManager: PlayoffManager) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // 1. 顶部标题
        PlayoffHeader(playoffManager)

        // 2. 对阵图 (横向滚动)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 0.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp)
            ) {
                playoffManager.rounds.forEachIndexed { index, round ->
                    PlayoffRoundColumn(
                        round = round,
                        isFirstRound = index == 0,
                        isLastRound = index == playoffManager.rounds.size - 1
                    )
                    // 列与列之间留40dp间距，给横线和竖线汇聚留空间
                    if (index < playoffManager.rounds.size - 1) {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }
            }
        }

        // 3. 底部信息（仅季后赛期间显示）
        if (playoffManager.isPlayoffActive) {
            val currentOpponent = playoffManager.getCurrentOpponent()
            if (currentOpponent.isNotEmpty()) {
                PlayoffBottomInfo(
                    myWins = playoffManager.myWins,
                    myLosses = playoffManager.myLosses,
                    currentOpponent = currentOpponent
                )
            }
        }
    }
}

@Composable
fun PlayoffHeader(playoffManager: PlayoffManager) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        Text("🎯 季后赛", color = PrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("S${playoffManager.rounds.lastOrNull()?.title?.substringBefore("赛区：") ?: "1"} 2026 · 季后赛阶段", color = GrayColor, fontSize = 16.sp)
    }
    Divider(color = Color(0xFF2A2A4E), thickness = 1.dp)
}

@Composable
fun PlayoffRoundColumn(round: PlayoffRound, isFirstRound: Boolean, isLastRound: Boolean) {
    Column(
        modifier = Modifier.width(150.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            round.title,
            color = PrimaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 30.dp)
        )

        // 参照HTML的match-pair结构：每2场为一组，平分高度
        val pairs = round.matches.chunked(2)
        pairs.forEach { pair ->
            // 用Box包裹，绘制汇聚竖线
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                // 队伍列表：SpaceAround分布
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    pair.forEach { match ->
                        MatchCard(
                            match = match,
                            isFirstRound = isFirstRound,
                            isLastRound = isLastRound
                        )
                    }
                }

                // 右侧汇聚竖线（非最后一轮且pair有2个卡片配对时）
                // 竖线位于卡片右边缘+20dp处（在列间距40dp的中间，与卡片右侧横线端点对齐）
                // 用weight 1:2:1让竖线占中间50%（从25%到75%），连接两个卡片中心
                if (!isLastRound && pair.size == 2) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 20.dp)
                            .fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .weight(2f)
                                .background(LineColor)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(match: PlayoffMatch, isFirstRound: Boolean, isLastRound: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
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
            modifier = Modifier.width(150.dp)
        ) {
            // 单行显示：队名1 比分 - 比分 队名2
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamInline(match.team1, match.score1, match.team1Win, alignStart = true)
                Text("VS", color = GrayColor, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                TeamInline(match.team2, match.score2, match.team2Win, alignStart = false)
            }
        }
    }
}

@Composable
fun TeamInline(teamName: String, score: String, isWin: Boolean, alignStart: Boolean) {
    val color = when {
        isWin -> WinColor
        score != "0" -> LoseColor
        else -> GrayColor
    }
    val weight = if (isWin) FontWeight.Bold else FontWeight.Normal

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (alignStart) {
            Text(
                teamName,
                color = color,
                fontWeight = weight,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                score,
                color = color,
                fontWeight = weight,
                fontSize = 13.sp
            )
        } else {
            Text(
                score,
                color = color,
                fontWeight = weight,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                teamName,
                color = color,
                fontWeight = weight,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
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
                .border(1.dp, PrimaryColor, RoundedCornerShape(8.dp))
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
