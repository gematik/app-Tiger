/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TigerConfigurationPropertyDto from "@/types/TigerConfigurationPropertyDto";

export function useConfigurationLoader() {
  const CONFIGURATION_EDITOR_URL = 'global_configuration';

  async function loadConfigurationProperties(): Promise<Array<TigerConfigurationPropertyDto>> {
    try {
      const response = await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL);
      return await response.json();
    } catch (error) {
      console.error('Error fetching data:', error);
      throw error;
    }
  }

  async function loadSubsetOfProperties(keyPrefix: string) {
    try {
      const response = await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL + `/${keyPrefix}`);
      return await response.json();
    } catch (error) {
      console.error('Error fetching data:', error);
      throw error;
    }
  }

  return {loadConfigurationProperties, loadSubsetOfProperties}
}