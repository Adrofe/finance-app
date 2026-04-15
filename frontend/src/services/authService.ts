import axios from 'axios';

import { KEYCLOAK_BASE_URL, KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET, KEYCLOAK_REALM } from '../config/env';
import type { KeycloakTokenResponse } from '../types/auth';

export async function requestAccessToken(username: string, password: string): Promise<string> {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: KEYCLOAK_CLIENT_ID,
    username,
    password,
    scope: 'openid'
  });

  if (KEYCLOAK_CLIENT_SECRET) {
    body.append('client_secret', KEYCLOAK_CLIENT_SECRET);
  }

  const tokenUrl = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;
  const response = await axios.post<KeycloakTokenResponse>(tokenUrl, body.toString(), {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });

  return response.data.access_token;
}
