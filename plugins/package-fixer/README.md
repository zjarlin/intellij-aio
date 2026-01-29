# Package Fixer is an IntelliJ IDEA plugin action that aligns Java and Kotlin
package declarations with the directory structure for selected folders.

## Usage
1. Ensure the target directories are under a marked Source Root.
2. Select one or more directories in the Project view.
3. Run "(package-fixer) Fix Package Declarations (Recursive)" from the context menu.
4. Review the notification summary.

## Supported Files
- .java (PsiJavaFile)
- .kt (KtFile, non-script)

## Not Supported
- .kts scripts
- Import statements (only package declarations are updated)
- Files outside recognized source roots

## How It Works
- Expected package is resolved via JavaDirectoryService for each file's parent directory.
- Java package is updated through PsiJavaFile.
- Kotlin package directive is created or replaced via KtPsiFactory.

## Requirements
- IntelliJ IDEA with Java and Kotlin plugins enabled.

## License
- See the root `LICENSE`.
