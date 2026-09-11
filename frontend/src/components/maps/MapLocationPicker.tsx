import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { MapPin, Search, Navigation, ExternalLink, X, Crosshair } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';

// Custom SVG Pin Marker matching Shardeya Emerald theme
const createPinIcon = () =>
  L.divIcon({
    className: 'shardeya-map-pin',
    html: `
      <div style="position: relative; width: 36px; height: 42px; transform: translate(-50%, -100%); pointer-events: auto; cursor: grab;">
        <div style="background: #047857; color: white; width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 2.5px solid #ffffff; box-shadow: 0 4px 10px rgba(0,0,0,0.35);">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0Z"/>
            <circle cx="12" cy="10" r="3"/>
          </svg>
        </div>
        <div style="width: 0; height: 0; border-left: 6px solid transparent; border-right: 6px solid transparent; border-top: 8px solid #047857; margin: -2px auto 0 auto;"></div>
      </div>
    `,
    iconSize: [36, 42],
    iconAnchor: [18, 42],
  });

interface MapLocationPickerProps {
  latitude?: number | null;
  longitude?: number | null;
  onChange: (lat: number | null, lng: number | null, mapsUrl: string) => void;
  initialCity?: string;
}

// Fallback coordinate: Center of India (Nagpur / MP region)
const DEFAULT_CENTER: [number, number] = [23.1765, 75.7885]; // Default Ujjain, MP
const DEFAULT_ZOOM = 13;

export function MapLocationPicker({
  latitude,
  longitude,
  onChange,
  initialCity,
}: MapLocationPickerProps) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [isLocating, setIsLocating] = useState(false);
  const [manualLat, setManualLat] = useState<string>(latitude ? String(latitude) : '');
  const [manualLng, setManualLng] = useState<string>(longitude ? String(longitude) : '');

  // Keep manual inputs in sync when props change
  useEffect(() => {
    setManualLat(latitude !== null && latitude !== undefined ? String(latitude) : '');
    setManualLng(longitude !== null && longitude !== undefined ? String(longitude) : '');
  }, [latitude, longitude]);

  const updatePosition = (lat: number, lng: number, updateMap = true) => {
    const roundedLat = parseFloat(lat.toFixed(7));
    const roundedLng = parseFloat(lng.toFixed(7));
    const mapsUrl = `https://www.google.com/maps?q=${roundedLat},${roundedLng}`;

    setManualLat(String(roundedLat));
    setManualLng(String(roundedLng));
    onChange(roundedLat, roundedLng, mapsUrl);

    if (mapInstanceRef.current) {
      if (markerRef.current) {
        markerRef.current.setLatLng([roundedLat, roundedLng]);
      } else {
        const marker = L.marker([roundedLat, roundedLng], {
          icon: createPinIcon(),
          draggable: true,
        }).addTo(mapInstanceRef.current);

        marker.on('dragend', () => {
          const pos = marker.getLatLng();
          updatePosition(pos.lat, pos.lng, false);
        });

        markerRef.current = marker;
      }

      if (updateMap) {
        mapInstanceRef.current.setView([roundedLat, roundedLng], Math.max(mapInstanceRef.current.getZoom(), 15), {
          animate: true,
        });
      }
    }
  };

  const clearLocation = () => {
    setManualLat('');
    setManualLng('');
    onChange(null, null, '');
    if (markerRef.current && mapInstanceRef.current) {
      mapInstanceRef.current.removeLayer(markerRef.current);
      markerRef.current = null;
    }
  };

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialPos: [number, number] =
      latitude && longitude ? [latitude, longitude] : DEFAULT_CENTER;
    const initialZoom = latitude && longitude ? 15 : DEFAULT_ZOOM;

    const map = L.map(mapContainerRef.current, {
      center: initialPos,
      zoom: initialZoom,
      zoomControl: true,
      attributionControl: false,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);

    if (latitude && longitude) {
      const marker = L.marker([latitude, longitude], {
        icon: createPinIcon(),
        draggable: true,
      }).addTo(map);

      marker.on('dragend', () => {
        const pos = marker.getLatLng();
        updatePosition(pos.lat, pos.lng, false);
      });

      markerRef.current = marker;
    }

    // Click on map to place or move pin
    map.on('click', (e: L.LeafletMouseEvent) => {
      updatePosition(e.latlng.lat, e.latlng.lng, false);
    });

    mapInstanceRef.current = map;

    // Force map size invalidation after render
    setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      map.remove();
      mapInstanceRef.current = null;
      markerRef.current = null;
    };
  }, []);

  // Center on city if initialCity is provided and no coordinates yet
  useEffect(() => {
    if (!latitude && !longitude && initialCity && mapInstanceRef.current) {
      handleSearchLocation(initialCity);
    }
  }, [initialCity]);

  // Handle Geocoding Search via OpenStreetMap Nominatim
  const handleSearchLocation = async (queryText?: string) => {
    const q = (queryText ?? searchQuery).trim();
    if (!q) return;

    setIsSearching(true);
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(q)}&countrycodes=in&limit=1`,
        { headers: { 'Accept-Language': 'en' } }
      );
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        const lat = parseFloat(data[0].lat);
        const lon = parseFloat(data[0].lon);
        updatePosition(lat, lon, true);
      }
    } catch {
      // Ignore network errors gracefully
    } finally {
      setIsSearching(false);
    }
  };

  // Handle Device Current Location
  const handleUseCurrentLocation = () => {
    if (!navigator.geolocation) return;
    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setIsLocating(false);
        updatePosition(pos.coords.latitude, pos.coords.longitude, true);
      },
      () => {
        setIsLocating(false);
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  };

  // Handle Manual Lat/Lng Blur or Submit
  const applyManualCoordinates = () => {
    const lat = parseFloat(manualLat);
    const lng = parseFloat(manualLng);
    if (!isNaN(lat) && !isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      updatePosition(lat, lng, true);
    }
  };

  const hasCoordinates = latitude !== null && latitude !== undefined && longitude !== null && longitude !== undefined;
  const googleMapsUrl = hasCoordinates ? `https://www.google.com/maps?q=${latitude},${longitude}` : null;

  return (
    <div className="space-y-3 rounded-xl border bg-card p-4 shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <div className="flex size-7 items-center justify-center rounded-md bg-emerald-700/10 text-emerald-700">
            <MapPin className="size-4" />
          </div>
          <div>
            <Label className="text-sm font-semibold">Project Pin Location on Map</Label>
            <p className="text-xs text-muted-foreground">
              Click anywhere on the map or drag the pin to set the exact site location
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleUseCurrentLocation}
            disabled={isLocating}
            className="h-8 gap-1.5 text-xs"
          >
            <Navigation className={`size-3.5 ${isLocating ? 'animate-spin' : ''}`} />
            {isLocating ? 'Locating...' : 'Use My GPS'}
          </Button>

          {hasCoordinates && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={clearLocation}
              className="h-8 text-xs text-muted-foreground hover:text-destructive"
            >
              <X className="mr-1 size-3.5" />
              Clear Pin
            </Button>
          )}
        </div>
      </div>

      {/* Map Search Bar */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleSearchLocation();
              }
            }}
            placeholder="Search address, locality, or landmark (e.g. Ujjain Road, Indore)..."
            className="pl-9 text-xs"
          />
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => handleSearchLocation()}
          disabled={isSearching || !searchQuery.trim()}
          className="text-xs"
        >
          {isSearching ? 'Searching...' : 'Find on Map'}
        </Button>
      </div>

      {/* Interactive Leaflet Map View */}
      <div className="relative overflow-hidden rounded-lg border bg-muted">
        <div ref={mapContainerRef} className="h-[180px] w-full z-0" />

        {!hasCoordinates && (
          <div className="pointer-events-none absolute inset-x-0 bottom-2 flex justify-center">
            <span className="rounded-full bg-background/90 px-3 py-1 text-xs font-medium text-muted-foreground shadow-sm backdrop-blur-xs">
              👉 Click anywhere on map to drop project pin
            </span>
          </div>
        )}
      </div>

      {/* Coordinate Display and Google Maps Sync */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <Label className="text-xs text-muted-foreground">Latitude</Label>
          <div className="mt-1 flex items-center gap-1.5">
            <Input
              type="number"
              step="any"
              value={manualLat}
              onChange={(e) => setManualLat(e.target.value)}
              onBlur={applyManualCoordinates}
              placeholder="e.g. 23.1765"
              className="h-8 text-xs font-mono"
            />
          </div>
        </div>

        <div>
          <Label className="text-xs text-muted-foreground">Longitude</Label>
          <div className="mt-1 flex items-center gap-1.5">
            <Input
              type="number"
              step="any"
              value={manualLng}
              onChange={(e) => setManualLng(e.target.value)}
              onBlur={applyManualCoordinates}
              placeholder="e.g. 75.7885"
              className="h-8 text-xs font-mono"
            />
          </div>
        </div>
      </div>

      {hasCoordinates && googleMapsUrl && (
        <div className="flex items-center justify-between rounded-lg bg-emerald-50/70 p-2.5 text-xs text-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200">
          <div className="flex items-center gap-2">
            <Crosshair className="size-4 text-emerald-600" />
            <span>
              Pinned at: <span className="font-mono font-medium">{latitude?.toFixed(5)}, {longitude?.toFixed(5)}</span>
            </span>
          </div>
          <a
            href={googleMapsUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 font-medium text-emerald-700 hover:underline dark:text-emerald-300"
          >
            <span>View on Google Maps</span>
            <ExternalLink className="size-3" />
          </a>
        </div>
      )}
    </div>
  );
}
