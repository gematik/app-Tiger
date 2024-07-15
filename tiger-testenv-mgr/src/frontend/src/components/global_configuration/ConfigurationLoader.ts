/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TigerConfigurationPropertyDto from "@/types/TigerConfigurationPropertyDto";

export function useConfigurationLoader() {
  const CONFIGURATION_EDITOR_URL = 'global_configuration';

  async function loadConfigurationProperties(): Promise<Array<TigerConfigurationPropertyDto>> {
    try {
      const response = await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL + "/all");
      return await response.json();
    } catch (error) {
      console.error('Error fetching data:', error);
      throw error;
    }
  }

  async function loadSubsetOfProperties(keyPrefix: string) {
    try {
      const response = await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL + `/key/${keyPrefix}`);
      return await response.json();
    } catch (error) {
      console.error('Error fetching data:', error);
      throw error;
    }
  }

  async function deleteConfigurationProperty(toDelete: TigerConfigurationPropertyDto): Promise<Response> {
    try {
      return await fetch(import.meta.env.BASE_URL + "global_configuration",
        {
          method: "DELETE",
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(toDelete)
        }
      );
    } catch (error) {
      console.error("Error deleting configuration entry " + error)
      throw error;
    }
  }

  async function saveConfigurationProperty(toSave: TigerConfigurationPropertyDto): Promise<Response> {
    try {
      return await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL,
        {
          method: "PUT",
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(toSave)
        }
      )
    } catch (error) {
      console.error("Error updating configuration entry " + error)
      throw error;
    }
  }

  async function importConfig(file: File) {
    try {
      return await fetch(import.meta.env.BASE_URL + CONFIGURATION_EDITOR_URL + "/file",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/yaml"
          },
          body: await file.text()
        })
    } catch (error) {
      console.error("Error importing configuration file" + error);
      throw error;
    }
  }

  return {
    loadConfigurationProperties,
    loadSubsetOfProperties,
    deleteConfigurationProperty,
    saveConfigurationProperty,
    importConfig
  }
}