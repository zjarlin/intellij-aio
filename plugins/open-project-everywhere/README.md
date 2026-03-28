# Open Project Everywhere Search and open repositories directly from Search Everywhere.

## Features

- Press `Shift` twice and search local projects across one or more configured directories, defaulting to `~/IdeaProjects`.
- Search GitHub, GitLab, Gitee, and one custom Git host in parallel.
- Show the repository source category for every result.
- Open local matches directly or clone remote repositories into the first configured local directory and open them.
- Reuse credentials from this plugin for Git HTTPS clone operations.

## Configuration

Open `Settings | Tools | Open Project Everywhere` and configure any combination of:

- One or more local project directories, with `~/IdeaProjects` added by default and used first for cloning
- GitHub
- GitLab
- Gitee
- Custom Git host

GitHub should use the IDE GitHub sign-in flow or a personal access token. Username/password mode is only intended for self-hosted or compatible servers such as GitLab-like endpoints.
