package com.basketball.dynasty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
val BgColor = Color(0xFF1a1a2e)
val CardColor = Color(0xFF1f4068)
val PrimaryColor = Color(0xFFff6b35)
val WinColor = Color(0xFF4ade80)
val LoseColor = Color(0xFFf87171)

data class QuarterScore(val quarter: String, val myScore: Int, val oppScore: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    var currentScreen by remember { mutableStateOf("Home") }
    
    // 赛季状态
    var seasonNum by remember { mutableStateOf(1) }
    var wins by remember { mutableStateOf(0) }
    var losses by remember { mutableStateOf(0) }
    var gamesPlayed by remember { mutableStateOf(0) }
    var pastSeasons by remember { mutableStateOf(listOf<String>()) }
    val totalGames = 82

    // 比赛状态
    var gameNum by remember { mutableStateOf(1) } // 当前是第几场
    var currentOpponent by remember { mutableStateOf("湖人") }
    var quarterScores by remember { mutableStateOf(listOf<QuarterScore>()) }
    var currentQuarter by remember { mutableStateOf("第1节") }
    var myTotalScore by remember { mutableStateOf(0) }
    var oppTotalScore by remember { mutableStateOf(0) }
    var isGameActive by remember { mutableStateOf(false) }
    
    var myInput by remember { mutableStateOf("") }
    var oppInput by remember { mutableStateOf("") }

    val opponents = listOf("湖人", "快船", "勇士", "凯尔特人", "热火", "太阳", "掘金", "雄鹿", "76人", "公牛")

    fun checkSeasonEnd() {
        if(gamesPlayed >= totalGames) {
            val resultStr = if (wins > losses) "🏆 冠军" else "无缘季后赛"
            pastSeasons = pastSeasons + "S${seasonNum} - ${wins}胜 ${losses}负 - ${resultStr}"
            seasonNum++
            wins = 0; losses = 0; gamesPlayed = 0
        }
    }

    fun startNewGame() {
        currentOpponent = opponents.random()
        quarterScores = listOf()
        currentQuarter = "第1节"
        myTotalScore = 0
        oppTotalScore = 0
        gameNum = gamesPlayed + 1
        isGameActive = true
        currentScreen = "Match"
    }

    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = CardColor,
                contentColor = PrimaryColor
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(onClick = { currentScreen = "Home" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Home") PrimaryColor else CardColor)) {
                        Text("📊 赛季")
                    }
                    Button(onClick = { 
                        // 只有比赛未开始或已结束时，才新建比赛；否则仅切回比赛页面（保留比分）
                        if (!isGameActive) {
                            startNewGame()
                        } else {
                            currentScreen = "Match"
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Match" || currentScreen=="Result") PrimaryColor else CardColor)) {
                        Text("🏀 比赛")
                    }
                    Button(onClick = { currentScreen = "Playoff" }, colors = ButtonDefaults.buttonColors(containerColor = if(currentScreen=="Playoff") PrimaryColor else CardColor)) {
                        Text("🎯 季后赛")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (currentScreen) {
                "Home" -> HomeScreen(seasonNum, wins, losses, gamesPlayed, totalGames, pastSeasons)
                "Match" -> MatchScreen(
                    gameNum = gameNum,
                    currentOpponent = currentOpponent,
                    currentQuarter = currentQuarter,
                    myTotalScore = myTotalScore,
                    oppTotalScore = oppTotalScore,
                    quarterScores = quarterScores,
                    myInput = myInput,
                    oppInput = oppInput,
                    onMyInputChange = { myInput = it },
                    onOppInputChange = { oppInput = it },
                    onSubmit = {
                        val my = myInput.toIntOrNull() ?: 0
                        val opp = oppInput.toIntOrNull() ?: 0
                        
                        val newScore = QuarterScore(currentQuarter, my, opp)
                        quarterScores = quarterScores + newScore
                        myTotalScore += my
                        oppTotalScore += opp
                        myInput = ""
                        oppInput = ""

                        // 2节比赛制逻辑
                        if (currentQuarter == "第1节") {
                            currentQuarter = "第2节"
                        } else if (currentQuarter == "第2节") {
                            if (myTotalScore == oppTotalScore) {
                                currentQuarter = "加时赛1"
                            } else {
                                if (myTotalScore > oppTotalScore) wins++ else losses++
                                gamesPlayed++
                                isGameActive = false
                                currentScreen = "Result"
                            }
                        } else if (currentQuarter.contains("加时赛")) {
                            if (myTotalScore == oppTotalScore) {
                                val otNum = currentQuarter.filter { it.isDigit() }.toIntOrNull() ?: 1
                                currentQuarter = "加时赛${otNum + 1}"
                            } else {
                                if (myTotalScore > oppTotalScore) wins++ else losses++
                                gamesPlayed++
                                isGameActive = false
                                currentScreen = "Result"
                            }
                        }
                    }
                )
                "Result" -> ResultScreen(
                    myScore = myTotalScore,
                    oppScore = oppTotalScore,
                    oppName = currentOpponent,
                    quarterScores = quarterScores,
                    onBackHome = { 
                        checkSeasonEnd()
                        currentScreen = "Home" 
                    },
                    onNextGame = {
                        checkSeasonEnd()
                        startNewGame()
                    }
                )
                "Playoff" -> PlayoffScreen()
            }
        }
    }
}

@Composable
fun HomeScreen(season: Int, wins: Int, losses: Int, played: Int, total: Int, pastSeasons: List<String>) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏀 篮球王朝", color = PrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("S${season} · 2026赛季", color = Color.White, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("胜", color = Color.Gray)
                    Text("$wins", color = WinColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("负", color = Color.Gray)
                    Text("$losses", color = LoseColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("胜率", color = Color.Gray)
                    val rate = if (played > 0) (wins.toFloat() / played * 100).format(1) else "0.0"
                    Text("${rate}%", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Text(seasonStr, color = Color.White, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    gameNum: Int,
    currentOpponent: String, currentQuarter: String, myTotalScore: Int, oppTotalScore: Int,
    quarterScores: List<QuarterScore>, myInput: String, oppInput: String,
    onMyInputChange: (String) -> Unit, onOppInputChange: (String) -> Unit, onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🏀 比赛计分", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("第 ${gameNum} 场 · vs ${currentOpponent}", color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // 加时赛提示
        if(currentQuarter.contains("加时赛")) {
            Text("⚡ 比分相同！进入加时赛", color = PrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(currentQuarter, color = PrimaryColor, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // 大比分展示
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("我方", color = Color.White, fontSize = 16.sp)
            Text("$myTotalScore", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(":", color = Color.Gray, fontSize = 36.sp)
            Text("$oppTotalScore", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(currentOpponent.take(2), color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // 左右两个输入框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = myInput, onValueChange = onMyInputChange,
                label = { Text("我方得分") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardColor, unfocusedContainerColor = CardColor,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = PrimaryColor, unfocusedLabelColor = Color.Gray
                )
            )
            Text("VS", color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
            OutlinedTextField(
                value = oppInput, onValueChange = onOppInputChange,
                label = { Text("${currentOpponent}得分") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardColor, unfocusedContainerColor = CardColor,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = PrimaryColor, unfocusedLabelColor = Color.Gray
                )
            )
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
                item {
                    Text("累计：我方 $myTotalScore : $oppTotalScore ${currentOpponent}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical=4.dp))
                }
            }
        }
    }
}

@Composable
fun ResultScreen(
    myScore: Int, oppScore: Int, oppName: String, 
    quarterScores: List<QuarterScore>,
    onBackHome: () -> Unit, onNextGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉 比赛结束", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        val isWin = myScore > oppScore
        Text(if(isWin) "我方获胜！" else "遗憾落败", color = if(isWin) WinColor else LoseColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("最终比分", color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我方", color = Color.White)
            Text(" $myScore ", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(":", color = Color.Gray)
            Text(" $oppScore ", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(oppName.take(2), color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("📋 比赛详情", color = Color.White, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(quarterScores) { score ->
                Text("${score.quarter}: 我方 ${score.myScore} : ${score.oppScore} ${oppName}", color = Color.Gray, modifier = Modifier.padding(vertical=4.dp))
            }
            if(quarterScores.isNotEmpty()) {
                item {
                    Text("累计：我方 $myScore : $oppScore ${oppName}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical=4.dp))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBackHome, colors = ButtonDefaults.buttonColors(containerColor = CardColor), modifier = Modifier.weight(1f)) {
                Text("查看战绩")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onNextGame, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor), modifier = Modifier.weight(1f)) {
                Text("下一场")
            }
        }
    }
}

@Composable
fun PlayoffScreen() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎯 季后赛", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("S1 2026 · 4强赛", color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        
        val rounds = listOf("16强", "8强", "4强", "决赛")
        rounds.forEach { round ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = CardColor), modifier = Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                Text("当前轮次: $round", color = Color.White, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)
