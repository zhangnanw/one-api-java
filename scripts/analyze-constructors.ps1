# 分析哪些类的构造方法适合用 @RequiredArgsConstructor 替换
$files = Get-ChildItem -Path src\main\java -Recurse -Filter *.java

$results = @()

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw

    # 必须是 Spring 管理的类
    if ($content -notmatch '@(Service|Component|Configuration|RestController|Controller|Repository)\b') {
        continue
    }

    # 必须有手写构造方法（public ClassName(...) { ... }）
    if ($content -notmatch "(?s)public\s+\w+\s*\([^)]*\)\s*\{") {
        continue
    }

    # 必须有 final 字段
    if ($content -notmatch 'private\s+final\s+') {
        continue
    }

    $results += [PSCustomObject]@{
        File = $file.FullName.Replace((Get-Location).Path + '\', '')
        Lines = (Get-Content $file.FullName).Count
    }
$results | Format-Table -AutoSize