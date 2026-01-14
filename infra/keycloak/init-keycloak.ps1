
<# 
  init-keycloak.ps1
  Inicializa Keycloak (realm, cliente, roles, usuario) para el proyecto.
  Ejecutar: 
    PS> ./infra/keycloak/init-keycloak.ps1

  Requisitos:
    - Keycloak accesible en http://localhost:8080 (modo start-dev)
    - Credenciales de admin del realm master (por defecto admin/admin)
#>

# =========================
# Configuración
# =========================
$KeycloakBaseUrl = "http://localhost:8080"
$AdminRealm      = "master"
$AdminUser       = "admin"
$AdminPass       = "admin"

$TargetRealm     = "finance-app"
$ClientId        = "finance-client"
$ClientSecret    = "YvrVh88cBFzZIMwkf2IsVxrItLhHoHpZ"   # puedes cambiarlo si quieres
$UserUsername    = "adrofe"
$UserPassword    = "adrofe"
$UserEmail       = "adrofe@example.com"
$AssignUserRole  = "ADMIN"   # rol que asignaremos al usuario (opcional)

# =========================
# Helpers
# =========================
function Get-AdminToken {
    $body = @{
        grant_type = "password"
        client_id  = "admin-cli"
        username   = $AdminUser
        password   = $AdminPass
    }
    $tokenResp = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakBaseUrl/realms/$AdminRealm/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $body
    return $tokenResp.access_token
}

function Invoke-Keycloak {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$Token
    )
    $Headers = @{ Authorization = "Bearer $Token" }
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 6)
    } else {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
    }
}

function Try-Get {
    param([string]$Url,[string]$Token)
    try { 
        return Invoke-Keycloak -Method Get -Url $Url -Token $Token 
    } catch { 
        return $null 
    }
}

# =========================
# 1) Obtener token admin
# =========================
Write-Host "Obteniendo token de admin..." -ForegroundColor Cyan
$AdminToken = Get-AdminToken
if (-not $AdminToken) { throw "No se pudo obtener el token de admin. Revisa usuario/contraseña de master." }

# =========================
# 2) Crear realm (si no existe)
# =========================
Write-Host "Verificando/creando realm '$TargetRealm'..." -ForegroundColor Cyan
$realmExists = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm" -Token $AdminToken
if (-not $realmExists) {
    $realmBody = @{
        realm = $TargetRealm
        enabled = $true
        registrationAllowed = $false
        # Si quieres exigir email verificado, ajusta aquí y en el usuario
        loginWithEmailAllowed = $true
    }
    Invoke-Keycloak -Method Post -Url "$KeycloakBaseUrl/admin/realms" -Body $realmBody -Token $AdminToken | Out-Null
    Write-Host "Realm '$TargetRealm' creado."
} else {
    Write-Host "Realm '$TargetRealm' ya existe."
}

# =========================
# 3) Crear cliente confidential con direct grant
# =========================
Write-Host "Verificando/creando cliente '$ClientId'..." -ForegroundColor Cyan
$clients = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/clients?clientId=$ClientId" -Token $AdminToken
if (-not $clients -or $clients.Count -eq 0) {
    $clientBody = @{
        clientId                   = $ClientId
        name                       = $ClientId
        enabled                    = $true
        publicClient               = $false                 # confidential
        serviceAccountsEnabled     = $false
        directAccessGrantsEnabled  = $true                  # permite grant_type=password
        standardFlowEnabled        = $true                  # auth code (para futura web)
        implicitFlowEnabled        = $false
        bearerOnly                 = $false
        consentRequired            = $false
        # Client authentication / secret
        clientAuthenticatorType    = "client-secret"
        secret                     = $ClientSecret
        # URIs opcionales
        rootUrl                    = $KeycloakBaseUrl
        redirectUris               = @("*")
        webOrigins                 = @("*")
        # Opcional: default client scopes (incluye 'openid' vía built-ins)
        defaultClientScopes        = @("web-origins","roles","profile","email")
    }
    Invoke-Keycloak -Method Post -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/clients" -Body $clientBody -Token $AdminToken | Out-Null
    Write-Host "Cliente '$ClientId' creado."
} else {
    Write-Host "Cliente '$ClientId' ya existe."
}

# =========================
# 4) Crear roles de realm (USER, ADMIN)
# =========================
Write-Host "Verificando/creando roles de realm..." -ForegroundColor Cyan
$desiredRoles = @("USER","ADMIN")
foreach ($r in $desiredRoles) {
    try {
        $role = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/roles/$r" -Token $AdminToken
        if (-not $role) {
            Invoke-Keycloak -Method Post -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/roles" -Body @{ name = $r } -Token $AdminToken | Out-Null
            Write-Host "Rol '$r' creado."
        } else {
            Write-Host "Rol '$r' ya existe."
        }
    } catch {
        Write-Warning "No se pudo comprobar/crear el rol '$r': $($_.Exception.Message)"
    }
}

# =========================
# 5) Crear usuario, email verificado, sin required actions, password no temporal
# =========================
Write-Host "Verificando/creando usuario '$UserUsername'..." -ForegroundColor Cyan
$users = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users?username=$UserUsername" -Token $AdminToken
if (-not $users -or $users.Count -eq 0) {
    $userBody = @{
        username       = $UserUsername
        enabled        = $true
        email          = $UserEmail
        emailVerified  = $true
        firstName      = "Adrian"
        lastName       = "Rodríguez"
        requiredActions = @()        # sin required actions
    }
    Invoke-Keycloak -Method Post -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users" -Body $userBody -Token $AdminToken | Out-Null
    Write-Host "Usuario '$UserUsername' creado."
    # Recuperar ID
    Start-Sleep -Seconds 1
    $users = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users?username=$UserUsername" -Token $AdminToken
}
$userId = $users[0].id
if (-not $userId) { throw "No se pudo obtener el ID del usuario." }

# Fijar contraseña (no temporal)
Write-Host "Estableciendo contraseña del usuario (no temporal)..." -ForegroundColor Cyan
$credBody = @{
    type      = "password"
    value     = $UserPassword
    temporary = $false
}
Invoke-Keycloak -Method Put -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users/$userId/reset-password" -Body $credBody -Token $AdminToken | Out-Null

# Quitar required actions explícitamente (por si las hubiera)
Invoke-Keycloak -Method Put -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users/$userId" -Body @{
    requiredActions = @()
    emailVerified   = $true
    enabled         = $true
} -Token $AdminToken | Out-Null

# =========================
# 6) Asignar rol USER (opcional)
# =========================
if ($AssignUserRole) {
    Write-Host "Asignando rol '$AssignUserRole' al usuario..." -ForegroundColor Cyan
    $roleDef = Try-Get -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/roles/$AssignUserRole" -Token $AdminToken
    if ($roleDef) {
        Invoke-Keycloak -Method Post -Url "$KeycloakBaseUrl/admin/realms/$TargetRealm/users/$userId/role-mappings/realm" -Body @($roleDef) -Token $AdminToken | Out-Null
        Write-Host "Rol '$AssignUserRole' asignado."
    } else {
        Write-Warning "El rol '$AssignUserRole' no existe; no se asignó."
    }
}

# =========================
# 7) Test: obtener access_token con password grant
# =========================
Write-Host "Probando obtención de token (password grant)..." -ForegroundColor Cyan
$tokenBody = @{
  client_id     = $ClientId
  client_secret = $ClientSecret
  username      = $UserUsername
  password      = $UserPassword
  grant_type    = "password"
  scope         = "openid"
}
try {
    $resp = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakBaseUrl/realms/$TargetRealm/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $tokenBody
    Write-Host "¡Token obtenido correctamente!" -ForegroundColor Green
    Write-Output $resp
    Write-Host "`nAccess Token (recortado):"
    Write-Host ($resp.access_token.Substring(0,40) + "...")
} catch {
    Write-Warning "Fallo al obtener el token: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Warning $_.ErrorDetails.Message }
}
