# Vcs auto update

> Automatically pull latest changes from remote before you push, avoiding Git conflicts in IntelliJ IDEA.

## Features

- **🔄 Auto Pull Before Push**: Automatically triggers a `git pull` operation whenever you initiate a push from the IDE.
- **⚡ Conflict Prevention**: Ensures your local branch is up-to-date with the remote branch before attempting to push, significantly reducing merge conflicts.
- **🔔 Smart Notifications**: Provides balloon notifications for successful or failed pull operations.
- **⚙️ Configurable Options**:
  - Enable/Disable auto-pull globally.
  - Choose between `git pull` (merge) and `git pull --rebase`.
  - Toggle notifications.
- **🛠️ Manual Pull Action**: Adds a "Pull from Remote" action in the VCS group (after the Push action) for quick manual updates.

## How it works

The plugin listens for the `Vcs.Push` action invoked within IntelliJ IDEA. When detected, it executes a background pull operation according to your settings. After the pull completes, it automatically refreshes the local file system (VFS) and Git status so you see the changes immediately.

## Usage

1. **Go to Settings**: Navigate to `Settings` → `Tools` → `Vcs auto update`.
2. **Enable Feature**: Check the "Auto pull before push" option.
3. **Configure Style**: Choose "Use rebase when pulling" if you prefer a cleaner commit history.
4. **Push as usual**: Use `Git` → `Push` or the toolbar button. The plugin will handle the pull automatically.

## Requirements

- IntelliJ IDEA 2024.2+
- Git integration enabled

## License

MIT
