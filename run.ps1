$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$frontendPath = Join-Path $projectRoot 'frontend'
$backendPath = Join-Path $projectRoot 'backend'
$cachePath = Join-Path $projectRoot '.codex_tmp\npm-cache'
$logsPath = Join-Path $projectRoot '.codex_tmp\logs'

New-Item -ItemType Directory -Force -Path $cachePath, $logsPath | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $frontendPath 'node_modules'))) {
  Push-Location $frontendPath
  try {
    npm install --cache $cachePath
  }
  finally {
    Pop-Location
  }
}

$maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
$backendOutput = Join-Path $logsPath 'backend.out.log'
$backendError = Join-Path $logsPath 'backend.err.log'
$backendProcess = Start-Process -FilePath $maven `
  -ArgumentList 'spring-boot:run' `
  -WorkingDirectory $backendPath `
  -WindowStyle Hidden `
  -RedirectStandardOutput $backendOutput `
  -RedirectStandardError $backendError `
  -PassThru

Write-Host '后端正在启动：http://127.0.0.1:8080/api/health'
Write-Host '前端即将启动：http://127.0.0.1:5173/'
Write-Host "后端日志：$backendOutput"

try {
  Push-Location $frontendPath
  npm run dev
}
finally {
  Pop-Location
  if ($backendProcess -and -not $backendProcess.HasExited) {
    Stop-Process -Id $backendProcess.Id -Force
  }
}
