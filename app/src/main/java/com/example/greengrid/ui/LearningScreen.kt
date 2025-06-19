package com.example.greengrid.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.greengrid.data.AchievementManager
import com.example.greengrid.data.AchievementType
import android.content.Context

@Composable
fun LearningScreen() {
    var selectedSection by remember { mutableStateOf<LearningSection?>(null) }
    var showQuizDialog by remember { mutableStateOf(false) }
    var showSurveyDialog by remember { mutableStateOf(false) }
    var showAIChatDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lernen & Entdecken",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Entdecke die Welt des P2P Stromhandels",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(LearningSection.values()) { section ->
                LearningSectionCard(
                    section = section,
                    onClick = {
                        selectedSection = section
                        when (section) {
                            LearningSection.QUIZ -> showQuizDialog = true
                            LearningSection.SURVEY -> showSurveyDialog = true
                            LearningSection.AI_CHAT -> showAIChatDialog = true
                            LearningSection.INFO -> showInfoDialog = true
                        }
                    }
                )
            }
        }
    }

    // Quiz Dialog
    if (showQuizDialog) {
        QuizDialog(
            onDismiss = { showQuizDialog = false },
            context = LocalContext.current
        )
    }

    // Survey Dialog
    if (showSurveyDialog) {
        SurveyDialog(
            onDismiss = { showSurveyDialog = false }
        )
    }

    // AI Chat Dialog
    if (showAIChatDialog) {
        AIChatDialog(
            onDismiss = { showAIChatDialog = false }
        )
    }

    // Info Dialog
    if (showInfoDialog) {
        InfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }
}

enum class LearningSection(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
) {
    QUIZ(
        "Stromhandel Quiz",
        "Teste dein Wissen über P2P Stromhandel",
        Icons.Default.Info,
        androidx.compose.ui.graphics.Color(0xFF4CAF50)
    ),
    SURVEY(
        "Umfrage",
        "Teile deine Meinung und Erfahrungen",
        Icons.Default.List,
        androidx.compose.ui.graphics.Color(0xFF2196F3)
    ),
    AI_CHAT(
        "KI Assistent",
        "Stelle Fragen an unseren KI-Assistenten",
        Icons.Default.Notifications,
        androidx.compose.ui.graphics.Color(0xFF9C27B0)
    ),
    INFO(
        "Informationen",
        "Lerne mehr über P2P Stromhandel",
        Icons.Default.Info,
        androidx.compose.ui.graphics.Color(0xFFFF9800)
    )
}

@Composable
fun LearningSectionCard(
    section: LearningSection,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = section.color,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = section.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

const val QUIZ_GENERATION_PROMPT = """
Du bist ein Quiz‑Generator für GreenGrid, das Konzept einer dezentralen Peer‑to‑Peer‑Strombörse, entwickelt als Schülerprojekt (10.Jgst., Dietrich‑Bonhoeffer‑Gymnasium Oberasbach) im Rahmen des YES!‑Wettbewerbs. (Momentan noch eine Simulation.) GreenGrid ermöglicht Haushalten, überschüssigen PV‑Strom direkt untereinander zu handeln – lokal, transparent und klimafreundlich.

## Konzept des P2P‑Strommarktes
- **Direkter Handel**: Prosumenten (Erzeuger mit PV‑Anlage) und Konsumenten tauschen Strom ohne Zwischenhändler.
- **Lokale Energiegemeinschaften**: Stromflüsse werden primär im Quartier ausgeglichen, um Netzbelastung und Übertragungsverluste zu minimieren.
- **Marktparadigma**: Angebot & Nachfrage bestimmen dynamisch den Preis – Nutzer erleben Markterlöse und -kosten genauso wie auf echten Börsen.
- **Netzdienstleistungen**: Durch Lastverschiebung in Niedrigpreisphasen werden Spitzenlastkraftwerke entlastet und die Netzstabilität verbessert.
- **Transparenz & Nachvollziehbarkeit**: Alle Gebote, Transaktionen und Preisbildungen sind im System einsehbar – fördert Vertrauen und Bildung.

## Marktmodell in der Simulation
- **Grundtrend**: Tageszyklus durch Sinusfunktion (Spitzen- vs. Sparzeiten).
- **Zufallskomponente**: Weißes Rauschen für unerwartete Preissprünge.
- **Echtzeit‑Gebote**: Nutzer platzieren Kauf‑/Verkaufsorders.
- **Matching-Algorithmus**: Stundengenaues Zusammenführen von Angeboten – höchster Gebotspreis trifft niedrigstes Verkaufsangebot.
- **Virtuelles Clearing**: Nach jedem Intervall werden Transaktionen automatisch abgerechnet und Energieguthaben angepasst.
- **Speicherintegration**: Überschüsse können in Quartierspeicher oder virtuelle Pools eingelagert und später veräußert werden.

## Wissensgrundlage für Quizfragen
Nutze das gesamte Know‑how über:
- dezentrale Strommärkte und lokale Energiegemeinschaften
- dynamische Preisbildung (Sinus‑Trend, Rauschen, Angebot & Nachfrage)
- CO₂‑Einsparung durch Lastverschiebung
- Leaderboards & Achievements als Gamification-Elemente
- Live‑Daten (aWATTar‑API) und Preis‑Wecker-Funktionen
- automatisierte Trading‑Regeln und Speicherstrategien

BEREITS GESTELLTE FRAGEN IN DIESER SESSION:
[PREVIOUS_QUESTIONS]

Antworte NUR im folgenden Format (keine zusätzlichen Texte) und halte die Antwortmöglichkeiten so kurz wie möglich (max. 40 Zeichen). Und stelle sicher, dass du den Buchstaben für die richtige Antwortmöglichkeit wirklich Zufällig auswählst:

FRAGE: [Die Quizfrage hier]  
A: [Erste Antwortoption]  
B: [Zweite Antwortoption]  
C: [Dritte Antwortoption]  
D: [Vierte Antwortoption]  
KORREKT: [A, B, C oder D]  

Beispiel:
FRAGE: Was bedeutet P2P Stromhandel?  
A: Professional Power Trading  
B: Power‑to‑Power Handel  
C: Peer‑to‑Peer Stromhandel zwischen Privatpersonen  
D: Public Power Transfer  
KORREKT: C

Erstelle jetzt eine neue, interessante Frage, die sich von den bereits gestellten unterscheidet. Die richtige Antwort sollte A, B, C oder D sein.
"""

data class GeneratedQuiz(
    val question: String,
    val options: List<String>,
    val correctAnswer: Int
)

data class QuizSession(
    val totalQuestions: Int,
    val questions: MutableList<GeneratedQuiz> = mutableListOf(),
    val answeredQuestions: MutableList<Int> = mutableListOf(),
    val correctAnswers: MutableList<Int> = mutableListOf()
)

suspend fun generateQuizQuestion(previousQuestions: List<String>): GeneratedQuiz {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("QuizGenerator", "Generating new quiz question")
            
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY")
            
            val previousQuestionsText = if (previousQuestions.isNotEmpty()) {
                previousQuestions.joinToString("\n") { "- $it" }
            } else {
                "Keine Fragen gestellt"
            }
            
            val prompt = QUIZ_GENERATION_PROMPT.replace("[PREVIOUS_QUESTIONS]", previousQuestionsText)
            
            // Properly escape the prompt for JSON
            val escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            
            val requestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "text": "$escapedPrompt"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            connection.outputStream.use { os ->
                val input = requestBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d("QuizGenerator", "Response code: $responseCode")
            
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("QuizGenerator", "API Error: $errorStream")
                Log.e("QuizGenerator", "Using fallback quiz due to API error")
                return@withContext getFallbackQuiz()
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            Log.d("QuizGenerator", "Response received: ${response.take(200)}...")
            
            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val result = parts.getJSONObject(0).optString("text", "")
                    Log.d("QuizGenerator", "Full AI response: $result")
                    return@withContext parseQuizResponse(result)
                } else {
                    Log.w("QuizGenerator", "No parts found in response, using fallback")
                }
            } else {
                Log.w("QuizGenerator", "No candidates found in response, using fallback")
            }
            
            Log.w("QuizGenerator", "No valid response structure found, using fallback")
            getFallbackQuiz()
            
        } catch (e: Exception) {
            Log.e("QuizGenerator", "Exception during quiz generation", e)
            getFallbackQuiz()
        }
    }
}

fun parseQuizResponse(response: String): GeneratedQuiz {
    try {
        Log.d("QuizGenerator", "Parsing response: ${response.take(100)}...")
        val lines = response.trim().split("\n")
        var question = ""
        val options = mutableListOf<String>()
        var correctAnswer = 0
        
        for (line in lines) {
            val trimmedLine = line.trim()
            Log.d("QuizGenerator", "Parsing line: '$trimmedLine'")
            when {
                trimmedLine.startsWith("FRAGE:") -> {
                    question = trimmedLine.substringAfter("FRAGE:").trim()
                    Log.d("QuizGenerator", "Found question: $question")
                }
                trimmedLine.startsWith("A:") -> {
                    val option = trimmedLine.substringAfter("A:").trim()
                    options.add(option)
                    Log.d("QuizGenerator", "Found option A: $option")
                }
                trimmedLine.startsWith("B:") -> {
                    val option = trimmedLine.substringAfter("B:").trim()
                    options.add(option)
                    Log.d("QuizGenerator", "Found option B: $option")
                }
                trimmedLine.startsWith("C:") -> {
                    val option = trimmedLine.substringAfter("C:").trim()
                    options.add(option)
                    Log.d("QuizGenerator", "Found option C: $option")
                }
                trimmedLine.startsWith("D:") -> {
                    val option = trimmedLine.substringAfter("D:").trim()
                    options.add(option)
                    Log.d("QuizGenerator", "Found option D: $option")
                }
                trimmedLine.startsWith("KORREKT:") -> {
                    val correct = trimmedLine.substringAfter("KORREKT:").trim()
                    correctAnswer = when (correct.uppercase()) {
                        "A" -> 0
                        "B" -> 1
                        "C" -> 2
                        "D" -> 3
                        else -> 0
                    }
                    Log.d("QuizGenerator", "Found correct answer: $correct (index: $correctAnswer)")
                }
            }
        }
        
        Log.d("QuizGenerator", "Parsing result - Question: '$question', Options: $options, Correct: $correctAnswer")
        
        if (question.isNotEmpty() && options.size == 4) {
            Log.d("QuizGenerator", "Successfully parsed quiz: $question")
            return GeneratedQuiz(question, options, correctAnswer)
        } else {
            Log.w("QuizGenerator", "Invalid quiz format - Question empty: ${question.isEmpty()}, Options count: ${options.size}")
            Log.w("QuizGenerator", "Using fallback quiz")
            return getFallbackQuiz()
        }
    } catch (e: Exception) {
        Log.e("QuizGenerator", "Error parsing quiz response", e)
        return getFallbackQuiz()
    }
}

fun getFallbackQuiz(): GeneratedQuiz {
    return GeneratedQuiz(
        "Was bedeutet P2P Stromhandel?",
        listOf(
            "Peer-to-Peer Stromhandel zwischen Privatpersonen",
            "Power-to-Power Handel",
            "Professional Power Trading",
            "Public Power Transfer"
        ),
        0
    )
}

@Composable
fun QuizDialog(onDismiss: () -> Unit, context: Context) {
    var quizSession by remember { mutableStateOf<QuizSession?>(null) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var showQuestionCountDialog by remember { mutableStateOf(true) }
    var selectedQuestionCount by remember { mutableStateOf(5) }
    var showAnswerFeedback by remember { mutableStateOf(false) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }
    var lastSelectedAnswer by remember { mutableStateOf(-1) }
    var forceRecompose by remember { mutableStateOf(0) }

    // Reset currentQuestionIndex when starting new quiz
    LaunchedEffect(quizSession) {
        if (quizSession != null) {
            currentQuestionIndex = 0
            forceRecompose++
        }
    }

    // Question count selection dialog
    if (showQuestionCountDialog) {
        var questionCountText by remember { mutableStateOf("10") }
        var isError by remember { mutableStateOf(false) }
        
        Dialog(
            onDismissRequest = { /* Cannot dismiss */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(400.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Quiz-Länge wählen",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Wie viele Fragen möchtest du beantworten? (5-50)",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = questionCountText,
                            onValueChange = { 
                                questionCountText = it.filter { char -> char.isDigit() }
                                isError = false
                            },
                            label = { Text("Anzahl Fragen") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = isError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (isError) {
                            Text(
                                text = "Bitte eine Zahl zwischen 5 und 50 eingeben",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    
                    Button(
                        onClick = {
                            val count = questionCountText.toIntOrNull()
                            if (count != null && count in 5..50) {
                                selectedQuestionCount = count
                                showQuestionCountDialog = false
                                quizSession = QuizSession(selectedQuestionCount)
                            } else {
                                isError = true
                            }
                        },
                        enabled = questionCountText.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Quiz starten", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    // Generate initial quiz questions (only 2 to start)
    LaunchedEffect(quizSession) {
        quizSession?.let { session ->
            if (session.questions.isEmpty()) {
                Log.d("QuizDialog", "Starting to generate initial 2 questions")
                isGenerating = true
                val questions = mutableListOf<GeneratedQuiz>()
                
                // Only generate first 2 questions initially
                repeat(minOf(2, session.totalQuestions)) { questionIndex ->
                    try {
                        Log.d("QuizDialog", "Generating initial question ${questionIndex + 1}")
                        val previousQuestions = questions.map { it.question }
                        val question = generateQuizQuestion(previousQuestions)
                        questions.add(question)
                        Log.d("QuizDialog", "Generated initial question: ${question.question}")
                    } catch (e: Exception) {
                        Log.e("QuizDialog", "Error generating initial question ${questionIndex + 1}", e)
                        questions.add(getFallbackQuiz())
                    }
                }
                
                Log.d("QuizDialog", "Finished generating ${questions.size} initial questions")
                session.questions.addAll(questions)
                isGenerating = false
            }
        }
    }

    // Generate next question after answering current question
    LaunchedEffect(showAnswerFeedback) {
        // Moved to button click to avoid composition issues
    }

    // Answer feedback dialog
    if (showAnswerFeedback) {
        Dialog(
            onDismissRequest = { /* Cannot dismiss - prevents cheating */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(300.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lastAnswerCorrect) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (lastAnswerCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (lastAnswerCorrect) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (lastAnswerCorrect) "Richtig! 🎉" else "Falsch! 😔",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (lastAnswerCorrect) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (!lastAnswerCorrect && quizSession != null) {
                            if (currentQuestionIndex < quizSession!!.questions.size) {
                                val currentQuestion = quizSession!!.questions[currentQuestionIndex]
                                val correctAnswer = currentQuestion.options[currentQuestion.correctAnswer]
                                Text(
                                    text = "Richtige Antwort: $correctAnswer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = if (lastAnswerCorrect) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { 
                            Log.d("QuizDialog", "Weiter button clicked. Current index: $currentQuestionIndex, Total: ${quizSession!!.totalQuestions}")
                            showAnswerFeedback = false
                            
                            if (currentQuestionIndex < quizSession!!.totalQuestions - 1) {
                                currentQuestionIndex++
                                forceRecompose++
                                Log.d("QuizDialog", "Moving to next question. New index: $currentQuestionIndex")
                                
                                // Generate next question if needed
                                if (currentQuestionIndex >= quizSession!!.questions.size - 1 && 
                                    quizSession!!.questions.size < quizSession!!.totalQuestions &&
                                    !isGenerating) {
                                    
                                    Log.d("QuizDialog", "Generating next question ${quizSession!!.questions.size + 1}")
                                    isGenerating = true
                                    
                                    // Use coroutine scope to avoid composition issues
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                            val previousQuestions = quizSession!!.questions.map { it.question }
                                            Log.d("QuizDialog", "Previous questions for generation: ${previousQuestions.size}")
                                            
                                            val question = generateQuizQuestion(previousQuestions)
                                            quizSession!!.questions.add(question)
                                            Log.d("QuizDialog", "Generated next question: ${question.question}")
                                            isGenerating = false
                                        } catch (e: Exception) {
                                            Log.e("QuizDialog", "Error generating next question", e)
                                            quizSession!!.questions.add(getFallbackQuiz())
                                            isGenerating = false
                                        }
                                    }
                                }
                            } else {
                                Log.d("QuizDialog", "Quiz finished. Showing results.")
                                showResult = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Weiter", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    // Main quiz dialog
    if (quizSession != null && !showQuestionCountDialog) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .height(600.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    if (isGenerating) {
                        // Loading state
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Quiz wird generiert...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!showResult && quizSession!!.questions.isNotEmpty()) {
                        // Progress indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frage ${currentQuestionIndex + 1} von ${quizSession!!.totalQuestions}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Richtig: ${quizSession!!.correctAnswers.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Quiz: P2P Stromhandel",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Safety check for currentQuestionIndex
                        if (currentQuestionIndex < quizSession!!.questions.size) {
                            Log.d("QuizDialog", "Displaying question ${currentQuestionIndex + 1}: ${quizSession!!.questions[currentQuestionIndex].question}")
                            key(forceRecompose) {
                                Text(
                                    text = quizSession!!.questions[currentQuestionIndex].question,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(quizSession!!.questions[currentQuestionIndex].options) { option ->
                                    val optionIndex = quizSession!!.questions[currentQuestionIndex].options.indexOf(option)
                                    Button(
                                        onClick = {
                                            val isCorrect = optionIndex == quizSession!!.questions[currentQuestionIndex].correctAnswer
                                            lastAnswerCorrect = isCorrect
                                            lastSelectedAnswer = optionIndex
                                            
                                            if (isCorrect) {
                                                quizSession!!.correctAnswers.add(currentQuestionIndex)
                                            }
                                            quizSession!!.answeredQuestions.add(currentQuestionIndex)
                                            
                                            showAnswerFeedback = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = option,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Start,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            // Fallback if index is out of bounds
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Fehler beim Laden der Frage",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Quiz beenden", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    } else if (showResult) {
                        Text(
                            text = "Quiz beendet!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        Text(
                            text = "Dein Ergebnis: ${quizSession!!.correctAnswers.size} von ${quizSession!!.totalQuestions} Punkten",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        val percentage = (quizSession!!.correctAnswers.size.toFloat() / quizSession!!.totalQuestions * 100).toInt()
                        Text(
                            text = when {
                                percentage >= 80 -> "Ausgezeichnet! Du bist ein P2P Stromhandel Experte!"
                                percentage >= 60 -> "Gut gemacht! Du hast ein solides Verständnis."
                                else -> "Weiter üben! Du lernst schnell dazu."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Update achievements
                        LaunchedEffect(Unit) {
                            try {
                                val achievementManager = AchievementManager(context)
                                
                                // Update Quiz Master achievement (questions answered with 80%+ accuracy)
                                if (percentage >= 80) {
                                    val currentValue = achievementManager.getCurrentAchievementValue(AchievementType.QUIZ_MASTER)
                                    achievementManager.updateAchievement(AchievementType.QUIZ_MASTER, currentValue + quizSession!!.totalQuestions)
                                    Log.d("QuizDialog", "Updated QUIZ_MASTER: ${currentValue + quizSession!!.totalQuestions}")
                                }
                                
                                // Update Quiz Perfectionist achievement (20 questions with best score)
                                if (quizSession!!.totalQuestions >= 20) {
                                    val currentBest = achievementManager.getCurrentAchievementValue(AchievementType.QUIZ_PERFECTIONIST)
                                    if (percentage > currentBest) {
                                        achievementManager.updateAchievement(AchievementType.QUIZ_PERFECTIONIST, percentage.toDouble())
                                        Log.d("QuizDialog", "Updated QUIZ_PERFECTIONIST: $percentage")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("QuizDialog", "Error updating achievements", e)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Schließen", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SurveyDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .height(400.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "GreenGrid Umfrage",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Teile deine Meinung und Erfahrungen mit uns! Die Umfrage hilft uns dabei, GreenGrid weiter zu verbessern.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        uriHandler.openUri("https://greengrid.onepage.me/umfrage")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Zur Umfrage", style = MaterialTheme.typography.bodyLarge)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abbrechen", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

const val GEMINI_API_KEY = "AIzaSyD-yvCQBtVl3ENua5nF-_oHvuY45h7lpZU"
const val SYSTEM_PROMPT = "Du bist der offizielle GreenGrid‑Assistenz‑Agent. Dein Ziel ist es, Nutzerinnen und Nutzern in der GreenGrid‑App bestmöglich zu helfen. " +
        "Bevor jede Nutzeranfrage beantwortet wird, erhältst du folgende Vorinformationen:\n" +
        "\n" +
        "## Überblick über GreenGrid\n" +
        "GreenGrid ist eine dezentrale, digitale Peer‑to‑Peer‑Strombörse, entwickelt als Schülerprojekt (10. Jahrgangsstufe, Dietrich‑Bonhoeffer‑Gymnasium Oberasbach) " +
        "im Rahmen des YES!‑Wettbewerbs. Verantwortlich: Merlin Ortner & GreenGrid‑Team.\n" +
        "Ziel: Haushalte handeln überschüssigen PV‑Strom direkt untereinander – lokal, transparent, klimafreundlich.\n" +
        "\n" +
        "## Simulations‑App\n" +
        "1. Marktmechanik: Spielsimulation mit dynamischen Preisen (gekoppelte Sinus‑ & Rauschfunktionen) und realistischen Angebot‑&‑Nachfrage‑Einflüssen.\n" +
        "2. Leaderboard: Ranglisten für höchste CO₂‑Einsparung und höchsten virtuellen Profit.\n" +
        "3. Achievements: Erfolge für CO₂‑Sparen, geschickte Trades, Netzglättung, Speicherstrategie.\n" +
        "4. Automatisiertes Trading: Vollautomatische Kauf‑/Verkaufsprozesse nach individuellen Vorgaben, auch im Hintergrund.\n" +
        "\n" +
        "## Echtwelt‑Integration\n" +
        "- Live‑Preise & Prognosen:\n" +
        "  • Echtzeit‑Preisdaten alle 5 Minuten mit Zeitstempel.\n" +
        "  • 24‑Stunden‑Prognose via aWATTar‑API (viertelstündlich; Netzengpässe, Topografie, Wetter).\n" +
        "- Preis‑Wecker:\n" +
        "  • Manuell: Erinnerung bei Erreichen eines definierten Preispunktes.\n" +
        "  • Automatisch: Trigger beim prognostizierten Tagestiefpunkt.\n" +
        "- Speicherintegration: Virtuelles Speichern in Quartierspeichern oder virtuellen Pools; 'Energieguthaben' für späteren Verbrauch/Verkauf.\n" +
        "\n" +
        "## Teilnahme ohne eigene Erzeugung\n" +
        "- Als reiner Konsument ohne PV/Speicher teilnehmbar.\n" +
        "- Mit eigenem Speicher mehr Flexibilität; mit PV‑Anlage zusätzliche Prosumenten‑Rolle.\n" +
        "\n" +
        "## Zukunft & Echthandel\n" +
        "Derzeit Simulation mit virtuellem Geld; späterer realer Handel in Planung.\n" +
        "Steuern & Abgaben bei Echthandel: §20EStG, Stromsteuer (2,05ct/kWh), Netzentgelte, Konzessionsabgabe; EEG‑Umlage 0ct/kWh.\n" +
        "\n" +
        "## Kosten & Aufwand\n" +
        "- Investition PV+Speicher: ca.5.000€–15.000€.\n" +
        "- App‑Nutzung: 1–2× täglich dank Automatisierung ausreichend.\n" +
        "\n" +
        "## Umweltnutzen\n" +
        "- Lastverschiebung in Niedrigpreis‑/Überschusszeiten reduziert Spitzenlastkraftwerke & Übertragungsverluste.\n" +
        "- Tägliche CO₂‑Einsparung (marginaler vs. Basis‑Emissionsfaktor).\n" +
        "\n" +
        "## Regulatorisches\n" +
        "- P2P‑Verkauf EEG‑geförderter Anlagen erst nach Ablauf der Förderung; App nutzbar als Konsument und Speicherbetreiber.\n" +
        "\n" +
        "## Support & Kontakt\n" +
        "Weitergehende Fragen per E‑Mail an ortnermerlin@gmail.com; alle Details auf https://greengrid.onepage.me und https://greengrid.onepage.me/faq\n" +
        "\n" +
        "## Kommunikationsstil\n" +
        "- Freundlich, verständlich, motivierend.\n" +
        "- Fachbegriffe einfach erklären.\n" +
        "- Präzise Anweisungen, Beispiele, Zwischenschritte.\n" +
        "- Enthusiastisch für nachhaltige Energie.\n" +
        "- Immer kennzeichnen: Simulation vs. echte Preise.\n" +
        "\n" +
        "## Typische Aufgaben\n" +
        "1. Spielunterstützung (Matching, Leaderboards, Achievements).\n" +
        "2. Preis‑Prognosen (aWATTar‑API erläutern).\n" +
        "3. Wecker‑Konfiguration (Einrichtung & Zuverlässigkeit).\n" +
        "4. FAQ‑Antworten (Zitate & Links).\n" +
        "5. Strategie‑Tipps (allgemein, keine Finanzberatung).\n" +
        "\n" +
        "## Fehlerbehandlung\n" +
        "- Bei Unklarheiten gezielt nachfragen.\n" +
        "- Technische Probleme an Support verweisen.\n" +
        "\n" +
        "## Zusatzinfos\n" +
        "- Keine persönlichen Daten speichern oder preisgeben.\n" +
        "- Quellen immer nennen (z.B. „Laut aWATTar‑API …“).\n" +
        "- Weiterführende Infos: Website & FAQs.\n" +
        "\n" +
        "Antworten stets präzise, freundlich und kompetent auf alle Fragen rund um GreenGrid!"

suspend fun fetchGeminiResponse(userMessage: String, chatHistory: List<ChatMessage>): String {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("GeminiAPI", "Starting API call for: $userMessage")
            
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY")
            
            // Build conversation context
            val conversationContext = buildString {
                append(SYSTEM_PROMPT)
                append("\n\nKonversationsverlauf:\n")
                
                chatHistory.forEach { message ->
                    if (message.isUser) {
                        append("Nutzer: ${message.text}\n")
                    } else {
                        append("KI: ${message.text}\n")
                    }
                }
                
                append("\nAktuelle Frage: $userMessage")
            }
            
            // Properly escape the prompt for JSON
            val escapedPrompt = conversationContext
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            
            val requestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "text": "$escapedPrompt"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
            
            Log.d("GeminiAPI", "Request body length: ${requestBody.length}")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000 // 30 seconds
            connection.readTimeout = 30000 // 30 seconds
            
            connection.outputStream.use { os ->
                val input = requestBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d("GeminiAPI", "Response code: $responseCode")
            
            if (responseCode != 200) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("GeminiAPI", "API Error: $errorStream")
                return@withContext "[API-Fehler $responseCode: $errorStream]"
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            Log.d("GeminiAPI", "Response received, length: ${response.length}")
            
            val json = JSONObject(response)
            
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val result = parts.getJSONObject(0).optString("text", "[Keine Antwort erhalten]")
                    Log.d("GeminiAPI", "Successfully extracted response: ${result.take(100)}...")
                    return@withContext result
                }
            }
            
            Log.w("GeminiAPI", "No valid response structure found")
            "[Keine Antwort erhalten]"
            
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Exception during API call", e)
            "[Fehler bei der KI-Antwort: ${e.localizedMessage ?: e.message ?: "Unbekannter Fehler"}]"
        }
    }
}

@Composable
fun AIChatDialog(onDismiss: () -> Unit) {
    var message by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentQuestion by remember { mutableStateOf<String?>(null) }

    // Handle API call when currentQuestion changes
    LaunchedEffect(currentQuestion) {
        currentQuestion?.let { question ->
            isLoading = true
            val aiResponse = try {
                fetchGeminiResponse(question, chatHistory)
            } catch (e: Exception) {
                "[Fehler bei der KI-Antwort: ${e.localizedMessage}]"
            }
            chatHistory = chatHistory + ChatMessage(aiResponse, false)
            isLoading = false
            currentQuestion = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .height(700.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "KI Assistent",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatHistory) { message ->
                        ChatMessageItem(message)
                    }
                    if (isLoading) {
                        item {
                            Text("KI denkt ...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        placeholder = { Text("Stelle eine Frage...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (message.isNotBlank()) {
                                val userMessage = ChatMessage(message, true)
                                chatHistory = chatHistory + userMessage
                                currentQuestion = message
                                message = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Senden", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val uriHandler = LocalUriHandler.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Parse and display text with clickable links
                val textParts = parseTextWithLinks(message.text)
                textParts.forEach { part ->
                    when (part) {
                        is TextPart.Link -> {
                            Text(
                                text = part.text,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                ),
                                modifier = Modifier.clickable {
                                    uriHandler.openUri(part.url)
                                }
                            )
                        }
                        is TextPart.Text -> {
                            Text(
                                text = part.text,
                                color = if (message.isUser) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class TextPart {
    data class Text(val text: String) : TextPart()
    data class Link(val text: String, val url: String) : TextPart()
}

fun parseTextWithLinks(input: String): List<TextPart> {
    val parts = mutableListOf<TextPart>()
    val regex = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
    var lastIndex = 0
    
    regex.findAll(input).forEach { matchResult ->
        // Add text before the link
        if (matchResult.range.first > lastIndex) {
            parts.add(TextPart.Text(input.substring(lastIndex, matchResult.range.first)))
        }
        
        // Add the link
        val linkText = matchResult.groupValues[1]
        val linkUrl = matchResult.groupValues[2]
        parts.add(TextPart.Link(linkText, linkUrl))
        
        lastIndex = matchResult.range.last + 1
    }
    
    // Add remaining text
    if (lastIndex < input.length) {
        parts.add(TextPart.Text(input.substring(lastIndex)))
    }
    
    return parts
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .height(600.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Über GreenGrid",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Digitale P2P-Strombörse für Spiel, Lernen und echten Umweltnutzen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    item {
                        Text(
                            text = "1. Konzept & Zielsetzung",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GreenGrid ist eine web- und mobilbasierte Applikation, mit der Nutzer*innen Strom selbst simuliert handeln können – lokal, transparent und klimafreundlich. Anstelle eines zentralen Versorgers treten Haushalte direkt als Prosumenten (Eigenerzeuger) und Konsumenten in Kontakt, handeln Überschüsse untereinander und lernen dabei spielerisch die Dynamik dezentraler Energiemärkte kennen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    item {
                        Text(
                            text = "2. Hauptfunktionen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "• Simulierter Stromhandel: Nutzer*innen kaufen, speichern und verkaufen Strom zu dynamischen Preisen, die im Simulationsmodus durch gekoppelte Sinus- und Rauschfunktionen realistische Marktschwankungen abbilden.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Echtzeit-Preisdaten & 24-h-Prognose: Live-Daten direkt von der Strombörse, aktualisiert im 5-Minuten-Takt. Preisprognose der nächsten 24 Stunden über aWATTar-API.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Strom-Wecker: Wunschpreis festlegen oder automatische Wecker-Funktion zum prognostizierten Tagestiefpunkt – vollautomatisch, auch wenn die App geschlossen ist.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Gamification: Leaderboard-System motiviert durch Wettbewerbe um höchsten Profit und größte CO₂-Einsparung. Achievements belohnen Meilensteine.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    item {
                        Text(
                            text = "3. Technische Grundlage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "• Simulations-Engine: Gekoppelte Sinus- und Rauschfunktionen garantieren identische, realistische Preisverläufe auf allen Endgeräten.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Echtwelt-Modus: Smart-Meter-Daten und Börsenpreise fließen live ein. Smart Contracts sichern unveränderliche Transaktionen.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Prognosemodell: KI-gestützte Algorithmen und aWATTar-API garantieren zuverlässige Vorhersagen mit genauen Zeitfenstern.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    item {
                        Text(
                            text = "4. Teilnahme & Zielgruppen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "• Ohne eigene PV-Anlage: Jede*r kann als reiner Konsument starten. Speicher erhöht die Flexibilität.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Mit eigener Anlage: Prosument*innen speisen Überschüsse ein, handeln und speichern selbst. Nach EEG-Auslauf sind P2P-Verkäufe möglich.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "• Automatisierung: Handelsprozesse lassen sich vollständig nach individuellen Regeln konfigurieren – tägliches Check-In genügt.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    item {
                        Text(
                            text = "5. Projektstatus & Ausblick",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "GreenGrid ist ein Schülerprojekt der 10. Jahrgangsstufe des Dietrich-Bonhoeffer-Gymnasiums Oberasbach im Rahmen des YES! (Young Economic Solutions) Wettbewerbs. Entwickelt wurde die App von Merlin Ortner und dem GreenGrid-Team. Gegenwärtig ist die Plattform eine realitätsnahe Simulation – mittelfristig ist der Übergang zu echtem Handel mit realen Strommengen und monetären Transaktionen geplant.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    item {
                        Text(
                            text = "Mehr Informationen: greengrid.onepage.me",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(top = 16.dp)
                ) {
                    Text("Schließen", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean
) 