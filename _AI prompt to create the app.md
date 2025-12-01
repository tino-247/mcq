**Project Title:** Android MCQ Trainer App

**Project Goal:**
Create a native Android application (Kotlin-based) that allows users to learn and practice multiple-choice questions. The app should store questions locally, track user performance statistics, and offer various filtering and training modes to help users focus on areas needing improvement.

**Core Features:**

1.  **Local Database for Questions and Statistics:**
    *   Utilize a local Room database to store all questions and user statistics associated with each question.
    *   The database should persist data across app sessions.

2.  **Initial Data Loading from CSV:**
    *   On first launch, or if the database is empty, the app must load question data from a `questions.csv` file located in the app's `assets` folder.
    *   **CSV Columns:**
        *   `Kategorie` (String): The main category of the question (e.g., "Geographie").
        *   `Unter-Kategorie` (String): The sub-category (e.g., "Hauptstädte").
        *   `Frage #` (String): A unique identifier for the question from the CSV (e.g., "G001", "S002a").
        *   `Frage` (String): The text of the question itself.
        *   `Antwort A` (String): Text for choice A.
        *   `Antwort B` (String): Text for choice B.
        *   `Antwort C` (String): Text for choice C.
        *   `Antwort D` (String): Text for choice D.
        *   `Richtige Antwort` (String): The correct answer, indicated by its letter ("A", "B", "C", or "D").
        *   `Abbildung` (String, Optional): A numeric identifier (e.g., "1", "2", "15") corresponding to an image file if the question has an associated picture. If no image, this field is blank.

3.  **Question Structure in Database:**
    *   Each question entity in the database should store:
        *   An auto-generated primary key (ID).
        *   All fields from the CSV (Category, Sub-Category, CSV Question Number, Question Text, Options A-D, Correct Answer Letter, Image Identifier).
        *   **Statistics Fields (all default to 0 initially):**
            *   `timesAnswered` (Int): Total number of times this question has been answered.
            *   `timesCorrect` (Int): Total number of times this question has been answered correctly.
            *   `timesChosenA` (Int): Number of times option A was chosen.
            *   `timesChosenB` (Int): Number of times option B was chosen.
            *   `timesChosenC` (Int): Number of times option C was chosen.
            *   `timesChosenD` (Int): Number of times option D was chosen.
            *   `timesCorrectRecent` (Int): Counts consecutive correct answers for this question. Resets to 0 if answered incorrectly; increments if answered correctly.

4.  **Image Handling:**
    *   Some questions may have associated images.
    *   Image files (e.g., `image_1.jpg`, `image_2.png`, etc.) are stored locally in the `res/drawable` directory.
    *   The app constructs the drawable resource name by prefixing "image_" to the numeric identifier from the `Abbildung` column.

**Application Screens and Functionality:**

**Screen 1: Main Menu (`MainActivity`)**

*   **UI Elements:**
    *   App Title.
    *   **Category Spinner:** Dropdown to select a main category. Populated dynamically from distinct categories in the database. Includes an "All Categories" option.
    *   **Sub-Category Spinner:** Dropdown to select a sub-category. Populated dynamically based on the selected main category. Includes an "All Subcategories" option (for the selected category). Disabled or shows "Select Category First" if no main category is chosen.
    *   **"Only Train Weak Questions" Checkbox:**
        *   Label: "Only Train Weak Questions (Recent Streak < 3)".
        *   When checked, applicable quiz modes will filter for "weak" questions.
        *   **Definition of Weak:** A question is considered "weak" if `timesCorrectRecent < 3`.
    *   **"Start Quiz with Filters" Button:**
        *   Starts a quiz based on the selected Category, Sub-Category, and the state of the "Only Train Weak Questions" checkbox.
        *   If "All Categories" is selected, the sub-category filter is ignored (or shows "All Subcategories").
        *   If the "Weak Questions" checkbox is checked, only weak questions matching the category/sub-category filters are included.
    *   **"Start Full Quiz" Button:**
        *   Starts a quiz including questions from all categories and sub-categories.
        *   This button *also* respects the "Only Train Weak Questions" checkbox. If checked, it starts a quiz of all weak questions in the entire database. If unchecked, it's all questions from the entire database.
    *   **"View Statistics" Button:** Navigates to the Statistics Page.
    *   **"Clear All Data (Reset Database)" Button:**
        *   Prompts the user with a confirmation dialog.
        *   If confirmed, deletes all question data and statistics from the local database. (The app should reload from CSV on next launch if DB is empty).
    *   **"Export Database to CSV" Button:**
        *   Uses Storage Access Framework (SAF) to allow the user to choose a location and filename.
        *   Exports all questions and their current statistics (including `timesAnswered`, `timesCorrect`, chosen counts, and `timesCorrectRecent`) to a CSV file. The CSV format should be compatible with the import format, plus the statistics columns.
    *   **"Export SQLite DB File" Button:**
        *   Uses SAF to allow the user to choose a location and filename.
        *   Copies the raw SQLite database `.db` file.
        *   Should attempt a `PRAGMA wal_checkpoint(FULL);` before copying to ensure data integrity.

**Screen 2: Quiz Page (`QuizActivity`)**

*   **UI Elements (Top Row):**
    *   **Left:** Displays the `Frage #` (CSV question number, e.g., "G001").
    *   **Center:** Displays individual statistics for the current question: "Ans: [N], Ok: [X%], Streak: [Y]" (where N is `timesAnswered`, X% is `timesCorrect / timesAnswered`, Y is `timesCorrectRecent`). Shows "New, Streak: 0" if `timesAnswered` is 0.
    *   **Right:** Displays quiz progress (e.g., "1/10").
*   **UI Elements (Content Area - arranged vertically, image flexible):**
    *   **ImageView:** Displays the associated image if one exists for the current question. This view should be vertically flexible, taking up available space between the top row and the question text. Hidden if no image.
    *   **Question Text `TextView`**: Displays the current question's text.
    *   **`RadioGroup` with four `RadioButton`s:** Displays options A, B, C, D.
*   **UI Elements (Bottom):**
    *   **"Submit Answer" Button:** Fixed at the bottom of the screen.
*   **Functionality:**
    *   Displays one question at a time.
    *   **On RadioButton Click:**
        *   The correct answer option is immediately highlighted in green.
        *   Other options remain uncolored at this stage.
        *   The user can change their selected radio button; the green highlight on the correct answer persists.
    *   **On "Submit Answer" Button Click:**
        *   The user's selected answer is evaluated.
        *   The correct answer remains/turns green.
        *   If the user's selected answer was incorrect, their chosen radio button turns red. Other incorrect, unchosen options remain uncolored (or a neutral color).
        *   All radio buttons are disabled.
        *   The "Submit Answer" button text changes to "Next Question" (or "Show Results" if it's the last question).
        *   The following statistics for the current question are updated in the database:
            *   `timesAnswered` increments.
            *   If correct: `timesCorrect` increments, `timesCorrectRecent` increments.
            *   If incorrect: `timesCorrectRecent` resets to 0.
            *   The corresponding `timesChosen[A/B/C/D]` increments.
        *   The app automatically proceeds to the next question (or results screen). There is no artificial delay after submitting; the transition is immediate after feedback colors are shown.
    *   **End of Quiz:** When all questions in the current set are answered, a results summary is displayed (e.g., "Quiz Finished! Your score: X/Y"). A button ("Back to Main Menu") allows navigation back.

**Screen 3: Statistics Page (`StatisticsActivity`)**

*   **UI:** Uses a `RecyclerView` to display statistics.
*   **Display Format:**
    *   Data is grouped by `Kategorie`. Each category name is a header.
    *   Under each category header, its `Unter-Kategorie`s are listed.
    *   For each `Unter-Kategorie`, the following aggregate statistics are shown:
        *   Sub-Category Name.
        *   "Total Questions: N" (total questions in this sub-category).
        *   "Questions Answered: X / N" (number of distinct questions in this sub-category that have `timesAnswered > 0`).
        *   "Avg. Correctness (all attempts): Y%" (calculated as `sum(timesCorrect) / sum(timesAnswered)` for all questions in this sub-category).
    *   **Action Buttons per Sub-Category:** Under each sub-category's stats, display three small buttons horizontally:
        *   **"All" Button:** Starts a quiz with all questions from this specific category and sub-category.
        *   **"Unanswered" Button:** Starts a quiz with only questions from this category and sub-category that have `timesAnswered == 0`.
        *   **"Weak" Button:** Starts a quiz with only "weak" questions (defined as `timesCorrectRecent < 3`) from this specific category and sub-category.
        *   **"Reset" Button:**
            *   Prompts the user with a confirmation dialog.
            *   If confirmed, resets all statistics (`timesAnswered`, `timesCorrect`, `timesChosenA-D`, `timesCorrectRecent` to 0) for ALL questions belonging to this specific category and sub-category.
            *   The statistics page should refresh to reflect the reset.
*   **Navigation:** A toolbar with a back button to return to the Main Menu.

**Technical Requirements & Architecture:**

*   **Language:** Kotlin.
*   **Minimum SDK:** API 21 (or higher as appropriate for libraries).
*   **Architecture:** MVVM (Model-View-ViewModel) is recommended.
    *   **Model:** Room Entities (`Question`), Repository.
    *   **View:** Activities (`MainActivity`, `QuizActivity`, `StatisticsActivity`), XML Layouts, Adapter for RecyclerView.
    *   **ViewModel:** `QuizViewModel`, `StatisticsViewModel` (to hold UI state, interact with Repository, expose LiveData).
*   **Libraries:**
    *   Android Jetpack: Room, ViewModel, LiveData, Navigation (optional, can use Intents), Activity KTX, Lifecycle KTX.
    *   Coroutines for asynchronous operations (database access, etc.).
    *   ViewBinding for accessing views in XML.
    *   OpenCSV (or similar) for CSV parsing during initial data load.
    *   `RecyclerView` for displaying lists (e.g., on the Statistics page).
    *   `ConstraintLayout` for flexible and complex layouts (especially `QuizActivity`).
*   **Error Handling:** Basic error handling (e.g., for file operations, database issues, invalid data).
*   **UI/UX:** Clean, intuitive, and responsive user interface. Use Material Design components where appropriate.

**Key Data Flow for Quiz Start:**

*   `MainActivity` or `StatisticsActivity` gathers parameters (category, sub-category, `onlyWeak` flag, `learningMode`).
*   An `Intent` is created for `QuizActivity` with these parameters as extras.
*   `QuizActivity.onCreate()` reads these extras.
*   Based on the extras (especially `learningMode` from Statistics or `onlyWeak` from Main), `QuizActivity` calls the appropriate method on `QuizViewModel` (e.g., `startQuiz`, `startUnansweredQuizForSubCategory`).
*   `QuizViewModel` calls `QuestionRepository` to fetch the relevant list of `Question` objects.
*   `QuestionRepository` calls `QuestionDao` to execute the appropriate SQL query.
*   `QuizViewModel` updates `LiveData` with the fetched questions.
*   `QuizActivity` observes this `LiveData` and displays the questions.

---

This detailed description should provide a solid foundation for recreating the project. Key aspects are the data structure, the different quiz modes and filtering logic, the statistics tracking, and the specific UI interactions on each screen.