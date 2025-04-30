# GitHub Smart Approval Bot - Java Implementation

A Spring Boot application that intelligently manages GitHub code review approvals by selectively dismissing only those approvals affected by changes to specific files.

## Features

- Integrates with GitHub's API to monitor pull request events
- Parses CODEOWNERS files to determine file ownership
- Tracks file changes between commits within a pull request
- Selectively dismisses only approvals relevant to modified files
- Preserves approvals from code owners whose files remain unchanged
- Provides clear comments explaining which approvals were cleared and why
- Includes configuration options to customize behavior per repository

## System Requirements

- Java 11 or higher
- Maven 3.6+ or Docker
- A GitHub account with permissions to create GitHub Apps

## Setting Up the GitHub App

1. Navigate to your GitHub account settings > Developer settings > GitHub Apps
2. Click "New GitHub App"
3. Fill in the following details:
   - **Name**: Smart Approval Bot
   - **Homepage URL**: (Your app's homepage or repository URL)
   - **Webhook URL**: (URL where your app will be hosted, e.g., https://your-domain.com/webhook)
   - **Webhook Secret**: Generate a secure random string

4. Permissions needed:
   - **Repository permissions**:
     - **Pull requests**: Read & write
     - **Contents**: Read-only
     - **Metadata**: Read-only
   - **Subscribe to events**:
     - Pull request

5. Click "Create GitHub App"
6. Generate a private key and download it
7. Note your App ID

## Installation

### Method 1: Running with Maven

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/smart-approval-bot.git
   cd smart-approval-bot
   ```

2. Configure the application:
   
   Create a file `src/main/resources/application-local.properties` with your GitHub App settings:
   
   ```properties
   github.app.id=YOUR_APP_ID
   github.app.name=smart-approval-bot
   github.app.private-key-path=/path/to/your/private-key.pem
   github.app.webhook-secret=YOUR_WEBHOOK_SECRET
   ```

3. Build and run the application:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

### Method 2: Running with Docker

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/smart-approval-bot.git
   cd smart-approval-bot
   ```

2. Create a `.env` file with your configuration:
   ```
   GITHUB_APP_ID=your_app_id
   GITHUB_APP_NAME=smart-approval-bot
   GITHUB_APP_WEBHOOK_SECRET=your_webhook_secret
   ```

3. Copy your private key to the `config` directory:
   ```bash
   mkdir -p config
   cp /path/to/your/private-key.pem config/
   ```

4. Build and run with Docker Compose:
   ```bash
   docker-compose up -d
   ```

## Repository Configuration

Create a `.github/smart-approval-config.json` file in your repository to configure the app:

```json
{
  "enabled": true,
  "notifyOnDismissal": true,
  "excludePaths": [
    "docs/*.md",
    "LICENSE",
    "README.md",
    "CHANGELOG.md"
  ],
  "strictMode": false,
  "commentTemplate": "## Smart Approval Update\n\nApprovals have been dismissed for: {{owners}}\n\nModified files: {{files}}"
}
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable selective approval management |
| `notifyOnDismissal` | boolean | `true` | Whether to add comments when approvals are dismissed |
| `excludePaths` | array | `[]` | File patterns to exclude from approval management |
| `strictMode` | boolean | `false` | If true, requires all files to have code owners |
| `commentTemplate` | string | (Default template) | Custom template for dismissal comments |

## CODEOWNERS Format

The app uses the standard GitHub CODEOWNERS file format:

```
# Example CODEOWNERS file
*.js    @javascript-team
*.py    @python-team
/docs/  @docs-team
```

Place this file in one of:
- `.github/CODEOWNERS`
- `CODEOWNERS`
- `docs/CODEOWNERS`
- `.gitlab/CODEOWNERS`

## How It Works

1. The app receives a webhook event when new commits are pushed to a pull request
2. It reads the repository's CODEOWNERS file to determine file ownership
3. It identifies the files modified in the new commits
4. It matches modified files to their code owners
5. It selectively dismisses approvals only from code owners whose files were changed
6. It adds a comment explaining which approvals were dismissed and why

## Architecture

The application follows a standard Spring Boot architecture:

- **Controllers**: Handle incoming webhook events
- **Services**: Contain business logic for interacting with GitHub API and processing files
- **Models**: Represent data structures used in the application
- **Config**: Contains application configuration classes

## Troubleshooting

### Common Issues

1. **Application fails to start**
   - Check that your private key file path is correct
   - Verify that the GitHub App ID is correct
   - Ensure Java 11+ is installed

2. **Webhook events not being received**
   - Verify your webhook URL is accessible from the internet
   - Check that the webhook secret matches
   - Confirm the correct events are subscribed to in the GitHub App settings

3. **Approvals not being dismissed**
   - Check that the CODEOWNERS file exists in the repository
   - Verify that the app has the necessary permissions
   - Ensure the repository configuration has `enabled` set to true

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
