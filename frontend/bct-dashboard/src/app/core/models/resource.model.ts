export type ResourceType = 'SERVER' | 'VIRTUAL_MACHINE' | 'APPLICATION' | 'DATABASE' | 'CONTAINER' | 'NETWORK_DEVICE';
export type ResourceStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN' | 'MAINTENANCE';

export interface Resource {
  id: number;
  resourceId: string;
  name: string;
  type: ResourceType;
  host: string;
  ipAddress: string;
  port: number;
  environment: string;
  description: string;
  status: ResourceStatus;
  lastSeen: string;
  createdAt: string;
  tags: string;
  /**
   * Hérité de l'époque simulation (retirée du projet). Le backend l'envoie
   * encore, mais depuis qu'il n'existe plus qu'une seule ressource — toujours
   * réelle — cette information n'a plus de valeur à afficher : ce serait un
   * badge qui dit toujours la même chose. Volontairement non utilisé côté UI.
   */
  simulated?: boolean;
}

export interface ResourceStats {
  total: number;
  up: number;
  down: number;
  degraded: number;
  unknown: number;
}
