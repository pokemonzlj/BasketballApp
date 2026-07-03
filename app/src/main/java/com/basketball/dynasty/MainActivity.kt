package com.basketball.dynasty

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
val BgColor = Color(0xFF1a1a2e)
val CardColor = Color(0xFF1f4068)
val PrimaryColor = Color(0xFFff6b35)
val WinColor = Color(0xFF4ade80)
val LoseColor = Color(0xFFf87171)
val GrayColor = Color(0xFF888888)
val LineColor = Color(0xFF4A6FA5)

data class QuarterScore(val quarter: String, val myScore: Int, val oppScore: Int)
data class GameRecord(val gameNum: Int, val opponent: String, val myScore: Int, val oppScore: Int, val isWin: Boolean, val otCount: Int)

object DBConfig {
    const val HOST = "sh-cdb-22fpc9ke.sql.tencentcdb.com"
    const val PORT = 20512
    const val USER = "v5_test"
    const val PASS = "zhongmai@69af"
    const val NAME = "shopv5"
    val isEnabled get() = HOST.isNotEmpty()
}

// 29支对手球队
val allOpponents = listOf(
    "老鹰", "凯尔特人", "公牛", "骑士", "独行侠", "掘金", "活塞", "勇士", "火箭", "步行者",
    "快船", "湖人", "灰熊", "热火", "雄鹿", "森林狼", "鹈鹕", "尼克斯", "魔术", "76人",
    "太阳", "开拓者", "国王", "马刺", "猛龙", "爵士", "奇才", "篮网", "雷霆"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("BasketballData", Context.MODE_PRIVATE)
        setContent {
            AppContent(prefs)
        }
    }
}

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

    fun startPlayoffs() {
        rounds.clear()
        myWins = 0
        myLosses = 0
        isPlayoffActive = true
        
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
        val currentRound = rounds.last()
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

        if (myWins == 4) {
            advanceToNextRound(currentRound)
        }
    }

    private fun advanceToNextRound(currentRound: PlayoffRound) {
        val winners = mutableListOf<String>()
        currentRound.matches.forEach { match ->
            if (match.team1 == "我方" || match.team2 == "我方") {
                if (match.team1 == "我方") { match.team1Win = true; match.team2Win = false } 
                else { match.team2Win = true; match.team1Win = false }
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
            isPlayoffActive = false // 赛季结束
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
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(prefs: android.content.SharedPreferences) {
    val scope = rememberCoroutineScope()
    var dbStatus by remember { mutableStateOf("等待数据库操作...") }
    var currentScreen by remember { mutableStateOf("Home") }
    var detailSeasonCode by remember { mutableStateOf("") }
    
    var seasonNum by remember { mutableStateOf(prefs.getInt("seasonNum", 1)) }
    var wins by remember { mutableStateOf(prefs.getInt("wins", 0)) }
    var losses by remember { mutableStateOf(prefs.getInt("losses", 0)) }
    var gamesPlayed by remember { mutableStateOf(prefs.getInt("gamesPlayed", 0)) }
    var pastSeasons by remember { mutableStateOf((prefs.getString("pastSeasons", "") ?: "").split("\n").filter { it.isNotEmpty() }) }
    val totalGames = 82

    var schedule by remember { mutableStateOf((prefs.getString("schedule", "") ?: "").split(",").filter { it.isNotEmpty() }) }
    
    var gameNum by remember { mutableStateOf(prefs.getInt("gameNum", 1)) }
    var currentOpponent by remember { mutableStateOf(prefs.getString("currentOpponent", "湖人") ?: "湖人") }
    var currentQuarter by remember { mutableStateOf(prefs.getString("currentQuarter", "第1节") ?: "第1节") }
    var myTotalScore by remember { mutableStateOf(prefs.getInt("myTotalScore", 0)) }
    var oppTotalScore by remember { mutableStateOf(prefs.getInt("oppTotalScore", 0)) }
    var isGameActive by remember { mutableStateOf(prefs.getBoolean("isGameActive", false)) }
    var quarterScores by remember { 
        mutableStateOf((prefs.getString("quarterScores", "") ?: "").split(";").filter { it.isNotEmpty() }.map { val p = it.split(","); QuarterScore(p[0], p[1].toInt(), p[2].toInt()) }) 
    }
    
    var myInput by remember { mutableStateOf("") }
    var oppInput by remember { mutableStateOf("") }

    // 季后赛状态管理器
    val playoffManager = remember { PlayoffManager() }

    // 生成 82 场赛程：29队*2场(58场) + 随机24队*1场(24场)
    fun generateSchedule(): List<String> {
        val twoGames = allOpponents + allOpponents
        val extraGames = allOpponents.shuffled().take(24)
        return (twoGames + extraGames).shuffled()
    }

    fun saveToLocal(currentSchedule: List<String> = schedule) {
        prefs.edit().apply {
            putInt("seasonNum", seasonNum); putInt("wins", wins); putInt("losses", losses)
            putInt("gamesPlayed", gamesPlayed); putString("pastSeasons", pastSeasons.joinToString("\n"))
            putInt("gameNum", gameNum); putString("currentOpponent", currentOpponent); putString("currentQuarter", currentQuarter)
            putInt("myTotalScore", myTotalScore); putInt("oppTotalScore", oppTotalScore); putBoolean("isGameActive", isGameActive)
            putString("quarterScores", quarterScores.joinToString(";") { "${it.quarter},${it.myScore},${it.oppScore}" })
            putString("schedule", currentSchedule.joinToString(","))
        }.apply()
    }

    LaunchedEffect(Unit) {
        if (schedule.isEmpty()) {
            schedule = generateSchedule()
            saveToLocal(schedule)
        }

        if (DBConfig.isEnabled) {
            dbStatus = "正在连接数据库拉取历史..."
            scope.launch(Dispatchers.IO) {
                try {
                    val url = "jdbc:mysql://${DBConfig.HOST}:${DBConfig.PORT}/${DBConfig.NAME}?useSSL=false&allowPublicKeyRetrieval=true"
                    DriverManager.getConnection(url, DBConfig.USER, DBConfig.PASS).use { conn ->
                        val sql = "SELECT season_code, SUM(is_win) as w, COUNT(*) as total FROM games GROUP BY season_code ORDER BY season_code DESC"
                        conn.createStatement().use { stmt ->
                            stmt.executeQuery(sql).use { rs ->
                                val seasons = mutableListOf<String>()
                                var latestSeason = 1
                                var latestWins = 0
                                var latestLosses = 0
                                var latestGames = 0
                                var isFirst = true

                                while (rs.next()) {
                                    val sCode = rs.getString("season_code")
                                    val sNum = sCode.substring(1).toInt()
                                    val w = rs.getInt("w")
                                    val l = rs.getInt("total") - w
                                    val g = rs.getInt("total")

                                    if (isFirst) {
                                        latestSeason = sNum; latestWins = w; latestLosses = l; latestGames = g; isFirst = false
                                    } else {
                                        val resultStr = if (w > l) "🏆 冠军" else "无缘季后赛"
                                        seasons.add("S${sNum} - ${w}胜 ${l}负 - ${resultStr}")
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    seasonNum = latestSeason; wins = latestWins; losses = latestLosses
                                    gamesPlayed = latestGames; pastSeasons = seasons; gameNum = latestGames + 1
                                    dbStatus = "数据库连接成功，已同步"
                                }
                            }
                        }
                    }
                } catch (e: Throwable) { 
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        dbStatus = "数据库拉取失败: ${e.message}"
                    }
                }
            }
        } else {
            dbStatus = "未开启数据库 (本地模式)"
        }
    }

    fun uploadToDB() {
        dbStatus = "正在上传比赛数据到数据库..."
        scope.launch(Dispatchers.IO) {
            try {
                val url = "jdbc:mysql://${DBConfig.HOST}:${DBConfig.PORT}/${DBConfig.NAME}?useSSL=false&allowPublicKeyRetrieval=true"
                DriverManager.getConnection(url, DBConfig.USER, DBConfig.PASS).use { conn ->
                    val sql = "INSERT INTO games (season_code, game_num, opponent_name, my_total_score, opp_total_score, is_win, quarters_detail) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                              "ON DUPLICATE KEY UPDATE opponent_name=VALUES(opponent_name), my_total_score=VALUES(my_total_score), opp_total_score=VALUES(opp_total_score), is_win=VALUES(is_win), quarters_detail=VALUES(quarters_detail)"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, "S${seasonNum}"); stmt.setInt(2, gameNum); stmt.setString(3, currentOpponent)
                        stmt.setInt(4, myTotalScore); stmt.setInt(5, oppTotalScore)
                        stmt.setInt(6, if (myTotalScore > oppTotalScore) 1 else 0)
                        val jsonArray = JSONArray()
                        quarterScores.forEach { q ->
                            val obj = JSONObject(); obj.put("quarter", q.quarter); obj.put("my_score", q.myScore); obj.put("opp_score", q.oppScore)
                            jsonArray.put(obj)
                        }
                        stmt.setString(7, jsonArray.toString()); stmt.executeUpdate()
                        
                        withContext(Dispatchers.Main) {
                            dbStatus = "第${gameNum}场数据已成功写入数据库!"
                        }
                    }
                }
            } catch (e: Throwable) { 
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    dbStatus = "数据库写入失败: ${e.message}"
                }
            }
        }
    }

    fun checkSeasonEnd() {
        if(gamesPlayed >= totalGames && !playoffManager.isPlayoffActive) {
            // 常规赛打完，如果没有开启季后赛，则开启
            playoffManager.startPlayoffs()
        }
    }

    fun startNewGame() {
        if (playoffManager.isPlayoffActive) {
            // 季后赛模式：从 playoffManager 拿对手
            currentOpponent = playoffManager.getCurrentOpponent()
            if (currentOpponent.isEmpty()) {
                // 季后赛都打完了
                val resultStr = if (wins > losses) "🏆 冠军" else "无缘季后赛"
                pastSeasons = pastSeasons + "S${seasonNum} - ${wins}胜 ${losses}负 - ${resultStr}"
                seasonNum++; wins = 0; losses = 0; gamesPlayed = 0
                playoffManager.isPlayoffActive = false
                schedule = generateSchedule()
                saveToLocal(schedule)
                currentScreen = "Home"
                return
            }
            gameNum++ // 季后赛场次继续递增
        } else {
            // 常规赛模式
            if (gamesPlayed >= schedule.size) {
                checkSeasonEnd()
            }
            currentOpponent = schedule[gamesPlayed]
            gameNum = gamesPlayed + 1
        }
        
        quarterScores = listOf(); currentQuarter = "第1节"
        myTotalScore = 0; oppTotalScore = 0; isGameActive = true
        currentScreen = "Match"; saveToLocal()
    }

    Scaffold(
        bottomBar = {
            BottomAppBar(containerColor = CardColor, contentColor = PrimaryColor) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Button(onClick = { currentScreen = "Home" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Home" || currentScreen=="SeasonDetail") PrimaryColor else CardColor)) { Text("📊 赛季") }
                    Button(onClick = { if (!isGameActive) startNewGame() else currentScreen = "Match" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Match" || currentScreen=="Result") PrimaryColor else CardColor)) { Text("🏀 比赛") }
                    Button(onClick = { currentScreen = "Playoff" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Playoff") PrimaryColor else CardColor)) { Text("🎯 季后赛") }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().background(BgColor).padding(paddingValues).padding(16.dp)) {
            when (currentScreen) {
                "Home" -> HomeScreen(seasonNum, wins, losses, gamesPlayed, totalGames, pastSeasons, dbStatus, onSeasonClick = { code ->
                    if (DBConfig.isEnabled) { detailSeasonCode = code; currentScreen = "SeasonDetail" }
                })
                "SeasonDetail" -> SeasonDetailScreen(detailSeasonCode, onBack = { currentScreen = "Home" })
                "Match" -> MatchScreen(
                    gameNum, currentOpponent, currentQuarter, myTotalScore, oppTotalScore, quarterScores, myInput, oppInput,
                    onMyInputChange = { myInput = it }, onOppInputChange = { oppInput = it },
                    onSubmit = {
                        val my = myInput.toIntOrNull() ?: 0; val opp = oppInput.toIntOrNull() ?: 0
                        quarterScores = quarterScores + QuarterScore(currentQuarter, my, opp)
                        myTotalScore += my; oppTotalScore += opp; myInput = ""; oppInput = ""

                        if (currentQuarter == "第1节") { currentQuarter = "第2节" } 
                        else if (currentQuarter == "第2节" || currentQuarter.contains("加时赛")) {
                            if (myTotalScore == oppTotalScore) {
                                val otNum = currentQuarter.filter { it.isDigit() }.toIntOrNull() ?: 1
                                currentQuarter = "加时赛${otNum + 1}"
                            } else {
                                val isWin = myTotalScore > oppTotalScore
                                // 如果在季后赛中，同步记录结果
                                if (playoffManager.isPlayoffActive) {
                                    playoffManager.recordMyGameResult(isWin)
                                } else {
                                    if (isWin) wins++ else losses++
                                    gamesPlayed++
                                }
                                isGameActive = false; currentScreen = "Result"
                                if (DBConfig.isEnabled) uploadToDB()
                            }
                        }
                        saveToLocal()
                    }
                )
                "Result" -> ResultScreen(
                    myTotalScore, oppTotalScore, currentOpponent, quarterScores,
                    onBackHome = { checkSeasonEnd(); currentScreen = "Home" },
                    onNextGame = { checkSeasonEnd(); startNewGame() }
                )
                "Playoff" -> PlayoffScreen(playoffManager)
            }
        }
    }
}

@Composable
fun HomeScreen(season: Int, wins: Int, losses: Int, played: Int, total: Int, pastSeasons: List<String>, dbStatus: String, onSeasonClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏀 篮球王朝", color = PrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(dbStatus, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth().clickable { onSeasonClick("S${season}") }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("S${season} · 2026赛季 (点击查看详情)", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("胜", color = Color.Gray); Text("$wins", color = WinColor, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("负", color = Color.Gray); Text("$losses", color = LoseColor, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("胜率", color = Color.Gray)
                        val rate = if (played > 0) (wins.toFloat() / played * 100).format(1) else "0.0"
                        Text("${rate}%", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("常规赛进度: ${played} / ${total} 场", color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        Text("🏆 历史赛季", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        if (pastSeasons.isEmpty()) {
            Text("暂无历史赛季数据", color = Color.Gray, modifier = Modifier.padding(top=8.dp))
        } else {
            pastSeasons.reversed().forEach { seasonStr ->
                val code = seasonStr.split(" ").firstOrNull() ?: ""
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { onSeasonClick(code) }) {
                    Text("$seasonStr (查看详情)", color = Color.White, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun SeasonDetailScreen(seasonCode: String, onBack: () -> Unit) {
    var games by remember { mutableStateOf(listOf<GameRecord>()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(seasonCode) {
        if (DBConfig.isEnabled) {
            scope.launch(Dispatchers.IO) {
                try {
                    val url = "jdbc:mysql://${DBConfig.HOST}:${DBConfig.PORT}/${DBConfig.NAME}?useSSL=false&allowPublicKeyRetrieval=true"
                    DriverManager.getConnection(url, DBConfig.USER, DBConfig.PASS).use { conn ->
                        val sql = "SELECT game_num, opponent_name, my_total_score, opp_total_score, is_win, quarters_detail FROM games WHERE season_code = ? ORDER BY game_num ASC"
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setString(1, seasonCode)
                            stmt.executeQuery().use { rs ->
                                val list = mutableListOf<GameRecord>()
                                while (rs.next()) {
                                    val gameNum = rs.getInt("game_num")
                                    val opp = rs.getString("opponent_name")
                                    val my = rs.getInt("my_total_score")
                                    val oppScore = rs.getInt("opp_total_score")
                                    val isWin = rs.getInt("is_win") == 1
                                    val jsonStr = rs.getString("quarters_detail")
                                    var otCount = 0
                                    if (!jsonStr.isNullOrBlank()) {
                                        val arr = JSONArray(jsonStr)
                                        for (i in 0 until arr.length()) {
                                            if (arr.getJSONObject(i).getString("quarter").contains("加时赛")) otCount++
                                        }
                                    }
                                    list.add(GameRecord(gameNum, opp, my, oppScore, isWin, otCount))
                                }
                                games = list
                            }
                        }
                    }
                } catch (e: Throwable) { e.printStackTrace() }
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = CardColor)) { Text("← 返回") }
            Spacer(modifier = Modifier.width(16.dp))
            Text("${seasonCode} 赛季比赛详情", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Text("正在从数据库加载比赛记录...", color = Color.Gray, modifier = Modifier.padding(16.dp))
        } else if (games.isEmpty()) {
            Text("该赛季暂无比赛数据", color = Color.Gray, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(games) { game ->
                    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("第 ${game.gameNum} 场", color = Color.Gray, fontSize = 14.sp)
                                Text("vs ${game.opponent}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${game.myScore} : ${game.oppScore}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(if(game.isWin) "胜" else "负", color = if(game.isWin) WinColor else LoseColor, fontWeight = FontWeight.Bold)
                                if(game.otCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("(${game.otCount}OT)", color = PrimaryColor, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(gameNum: Int, currentOpponent: String, currentQuarter: String, myTotalScore: Int, oppTotalScore: Int, quarterScores: List<QuarterScore>, myInput: String, oppInput: String, onMyInputChange: (String) -> Unit, onOppInputChange: (String) -> Unit, onSubmit: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏀 比赛计分", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("第 ${gameNum} 场 · vs ${currentOpponent}", color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        if(currentQuarter.contains("加时赛")) {
            Text("⚡ 比分相同！进入加时赛", color = PrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(currentQuarter, color = PrimaryColor, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("我方", color = Color.White, fontSize = 16.sp)
            Text("$myTotalScore", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(":", color = Color.Gray, fontSize = 36.sp)
            Text("$oppTotalScore", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(currentOpponent.take(2), color = Color.White, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedTextField(value = myInput, onValueChange = onMyInputChange, label = { Text("我方得分") }, modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = CardColor, unfocusedContainerColor = CardColor, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = PrimaryColor, unfocusedLabelColor = Color.Gray))
            Text("VS", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedTextField(value = oppInput, onValueChange = onOppInputChange, label = { Text("${currentOpponent}得分") }, modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = CardColor, unfocusedContainerColor = CardColor, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = PrimaryColor, unfocusedLabelColor = Color.Gray))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) {
            Text(if(currentQuarter.contains("加时赛")) "完成${currentQuarter}" else "完成${currentQuarter} →")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("📋 本场比赛", color = Color.White, modifier = Modifier.align(Alignment.Start))
        LazyColumn {
            items(quarterScores) { score ->
                Text("${score.quarter}: 我方 ${score.myScore} : ${score.oppScore} ${currentOpponent}", color = Color.Gray, modifier = Modifier.padding(vertical=4.dp))
            }
            if(quarterScores.isNotEmpty()) {
                item { Text("累计：我方 $myTotalScore : $oppTotalScore ${currentOpponent}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical=4.dp)) }
            }
        }
    }
}

@Composable
fun ResultScreen(myScore: Int, oppScore: Int, oppName: String, quarterScores: List<QuarterScore>, onBackHome: () -> Unit, onNextGame: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎉 比赛结束", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        val isWin = myScore > oppScore
        Text(if(isWin) "我方获胜！" else "遗憾落败", color = if(isWin) WinColor else LoseColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("最终比分", color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我方", color = Color.White); Text(" $myScore ", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(":", color = Color.Gray)
            Text(" $oppScore ", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold); Text(oppName.take(2), color = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("📋 比赛详情", color = Color.White, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(quarterScores) { score ->
                Text("${score.quarter}: 我方 ${score.myScore} : ${score.oppScore} ${oppName}", color = Color.Gray, modifier = Modifier.padding(vertical=4.dp))
            }
            if(quarterScores.isNotEmpty()) {
                item { Text("累计：我方 $myScore : $oppScore ${oppName}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical=4.dp)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBackHome, colors = ButtonDefaults.buttonColors(containerColor = CardColor), modifier = Modifier.weight(1f)) { Text("查看战绩") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onNextGame, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor), modifier = Modifier.weight(1f)) { Text("下一场") }
        }
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
        PlayoffHeader()

        // 2. 对阵图 (支持横向滚动)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 0.dp, vertical = 20.dp)
        ) {
            Row(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
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
        val currentOpponent = if (playoffManager.isPlayoffActive) playoffManager.getCurrentOpponent() else "赛季已结束"
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
        Text("🎯 季后赛", color = PrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
            color = PrimaryColor,
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

fun Float.format(digits: Int) = "%.${digits}f".format(this)
