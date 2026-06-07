$basePath = "c:\Users\zqimi\OneDrive\Desktop\CPE Manager\app\src\main\java\com\cpemanager"  
Get-ChildItem -Path $basePath -Recurse -Filter "*.kt" | ForEach-Object {  
  $file = $_.FullName  
  $content = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)  
  $orig = $content  
  $content = $content.Replace([char]0x95F0,[char]0x914D)  
  Write-Host ("Done: " + $_.Name)  
} 
