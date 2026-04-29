export type KeycloakTokenResponse = {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
  id_token?: string;
};

export type KeycloakErrorResponse = {
  error?: string;
  error_description?: string;
};

export type PkceCallbackResult = {
  completed: boolean;
  accessToken: string | null;
  refreshToken?: string | null;
  error: string | null;
};
