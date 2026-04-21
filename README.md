# strain-synonym-fix-pipeline

Fixes separator characters in strain alias values in the ALIASES table.

## What it does

Scans aliases of type `old_strain_symbol` and `old_strain_name`. If an alias value contains `||` or `,` separators, they are replaced with `;` for consistency.

## Usage

```
java -jar strain-synonym-fix-pipeline.jar --fix_strain_synonyms
```

## Logging

- `logs/status.log` -- run summary with counts and details of each updated alias
