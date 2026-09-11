import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Navigation, ExternalLink, Compass } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

// Custom Emerald Marker for Project Overview
const createProjectMarkerIcon = () =>
  L.divIcon({
    className: 'project-overview-marker',
    html: `
      <div style="position: relative; width: 40px; height: 48px; transform: translate(-50%, -100%);">
        <div style="background: #047857; color: white; width: 38px; height: 38px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 3px solid #ffffff; box-shadow: 0 4px 12px rgba(0,0,0,0.35);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0Z"/>
            <circle cx="12" cy="10" r="3"/>
          </svg>
        </div>
        <div style="width: 0; height: 0; border-left: 7px solid transparent; border-right: 7px solid transparent; border-top: 10px solid #047857; margin: -3px auto 0 auto;"></div>
      </div>
    `,
    iconSize: [40, 48],
    iconAnchor: [20, 48],
    popupAnchor: [0, -48],
  });

interface ProjectLocationMapProps {
  latitude?: number | null;
  longitude?: number | null;
  googleMapsUrl?: string | null;
  projectName: string;
  address: string;
  locality: string;
  city: string;
  onEditLocation?: () => void;
}

export function ProjectLocationMap({
  latitude,
  longitude,
  googleMapsUrl,
  projectName,
  address,
  locality,
  city,
  onEditLocation,
}: ProjectLocationMapProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);

  const hasCoordinates =
    latitude !== null && latitude !== undefined && longitude !== null && longitude !== undefined;

  const resolvedMapsUrl =
    googleMapsUrl ||
    (hasCoordinates ? `https://www.google.com/maps?q=${latitude},${longitude}` : null);

  useEffect(() => {
    if (!hasCoordinates || !mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: [latitude, longitude],
      zoom: 15,
      zoomControl: true,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);

    const marker = L.marker([latitude, longitude], {
      icon: createProjectMarkerIcon(),
    }).addTo(map);

    marker
      .bindPopup(
        `<div style="font-family: inherit; padding: 2px;">
          <strong style="color: #047857; font-size: 13px;">${projectName}</strong><br/>
          <span style="font-size: 11px; color: #64748b;">${locality}, ${city}</span>
        </div>`
      )
      .openPopup();

    mapInstanceRef.current = map;

    setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [hasCoordinates, latitude, longitude, projectName, locality, city]);

  return (
    <Card className="overflow-hidden border-border/80 shadow-xs max-w-full md:max-w-[70%]">
      <CardHeader className="flex flex-row items-center justify-between pb-3">
        <div className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base font-semibold">
            <MapPin className="size-4 text-emerald-600" />
            Project Location
          </CardTitle>
          <CardDescription className="text-xs">
            {locality}, {city} • {address}
          </CardDescription>
        </div>

        <div className="flex items-center gap-2">
          {resolvedMapsUrl && (
            <Button asChild size="sm" variant="outline" className="gap-1.5 text-xs h-8">
              <a href={resolvedMapsUrl} target="_blank" rel="noopener noreferrer">
                <Navigation className="size-3.5 text-emerald-600" />
                Directions
                <ExternalLink className="size-3" />
              </a>
            </Button>
          )}

          {onEditLocation && (
            <Button size="sm" variant="ghost" onClick={onEditLocation} className="text-xs h-8">
              {hasCoordinates ? 'Adjust Pin' : 'Set Pin'}
            </Button>
          )}
        </div>
      </CardHeader>

      <CardContent className="p-0">
        {hasCoordinates ? (
          <div>
            <div ref={mapContainerRef} className="h-[200px] w-full z-0 border-y" />
            <div className="flex flex-wrap items-center justify-between gap-3 bg-muted/40 px-4 py-2.5 text-xs">
              <div className="flex items-center gap-2 text-muted-foreground">
                <Compass className="size-4 text-emerald-600" />
                <span>
                  GPS Coordinates:{' '}
                  <strong className="font-mono text-foreground font-medium">
                    {latitude?.toFixed(6)}° N, {longitude?.toFixed(6)}° E
                  </strong>
                </span>
              </div>
              <span className="text-muted-foreground">
                Interactive site view • OpenStreetMap & Leaflet
              </span>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center gap-3 p-8 text-center bg-muted/20 border-t">
            <div className="flex size-12 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700">
              <MapPin className="size-6" />
            </div>
            <div>
              <p className="text-sm font-medium">No Map Location Pinned Yet</p>
              <p className="text-xs text-muted-foreground max-w-sm mt-1">
                Pin the project location on the map to provide site directions and coordinates for buyers and partners.
              </p>
            </div>
            {onEditLocation && (
              <Button size="sm" onClick={onEditLocation} className="gap-1.5 text-xs">
                <MapPin className="size-3.5" />
                Pin Project Location
              </Button>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
