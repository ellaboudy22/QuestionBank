# QuestionBank

A Spring Boot application for managing questions and answers with AI-powered scoring and plagiarism detection.

## Tech Stack

- **PostgreSQL**
- **Mistral AI** for essay/code evaluation
- **Judge0 API** for code compilation
- **ONNX Runtime** for local ML embeddings

## Project Structure

```
src/main/java/com/questionbank/QuestionBank/
├── config/          - Application configuration
├── controller/      - REST API endpoints
├── dto/             - Data transfer objects
├── entity/          - Database entities
├── repository/      - Data access layer
├── service/         - Business logic
│   └── plagiarism/  - Plagiarism detection
├── security/        - JWT authentication
├── exception/       - Error handling
└── util/            - Utilities
```
