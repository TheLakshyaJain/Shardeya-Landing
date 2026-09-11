import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Database,
  Activity,
  ExternalLink,
  CheckCircle2,
  RefreshCw,
  HardDrive,
  FileCode,
  Mail,
  MessageSquare,
  Layers,
  ArrowLeft,
  Terminal,
  Copy,
  Check,
  Maximize2
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface ServiceCardData {
  title: string;
  category: 'database' | 'backend' | 'infra';
  description: string;
  port: number | string;
  url: string;
  icon: typeof Database;
  badge: string;
  credentials?: string;
}

const SERVICES: ServiceCardData[] = [
  {
    title: 'Database Web Studio (pgweb)',
    category: 'database',
    description: 'Zero-config visual PostgreSQL browser. Browse all 59 tables, inspect schema, edit records, and run SQL queries.',
    port: 8081,
    url: 'http://localhost:8081',
    icon: Database,
    badge: 'PostgreSQL Web UI',
  },
  {
    title: 'Backend API Docs (Swagger UI)',
    category: 'backend',
    description: 'Interactive OpenAPI documentation for all Builder, Broker, and Platform REST endpoints with "Try it out" feature.',
    port: 8080,
    url: 'http://localhost:8080/api/v1/swagger-ui/index.html',
    icon: FileCode,
    badge: 'Spring Boot 3.3',
  },
  {
    title: 'Backend Health (Actuator)',
    category: 'backend',
    description: 'Live Spring Boot system telemetry, database connection pool status, and liveness probe.',
    port: 8080,
    url: 'http://localhost:8080/actuator/health',
    icon: Activity,
    badge: 'JVM Telemetry',
  },
  {
    title: 'MailHog Local Email Inbox',
    category: 'infra',
    description: 'Webmail interface capturing all outgoing OTP verification codes, password resets, and staff invites.',
    port: 8025,
    url: 'http://localhost:8025',
    icon: Mail,
    badge: 'SMTP Capture',
  },
  {
    title: 'MinIO S3 Object Storage',
    category: 'infra',
    description: 'S3-compatible bucket explorer storing cadastral layout blueprints, brochures, and property media.',
    port: 9001,
    url: 'http://localhost:9001',
    icon: HardDrive,
    badge: 'S3 Storage',
    credentials: 'User: shardeya / Pass: shardeya123',
  },
  {
    title: 'WhatsApp Notification Stub',
    category: 'infra',
    description: 'Local simulation endpoint to inspect outgoing WhatsApp booking receipts and milestone alerts.',
    port: 4001,
    url: 'http://localhost:4001/v1/messages',
    icon: MessageSquare,
    badge: 'Webhook API',
  },
];

export function SystemPortalPage() {
  const [backendHealth, setBackendHealth] = useState<'UP' | 'DOWN' | 'CHECKING'>('CHECKING');
  const [pgwebHealth, setPgwebHealth] = useState<'UP' | 'DOWN' | 'CHECKING'>('CHECKING');
  const [copiedQuery, setCopiedQuery] = useState<string | null>(null);

  const checkHealth = async () => {
    setBackendHealth('CHECKING');
    setPgwebHealth('CHECKING');

    try {
      const res = await fetch('http://localhost:8080/actuator/health');
      if (res.ok) {
        const data = await res.json();
        setBackendHealth(data.status === 'UP' ? 'UP' : 'DOWN');
      } else {
        setBackendHealth('DOWN');
      }
    } catch {
      setBackendHealth('DOWN');
    }

    try {
      await fetch('http://localhost:8081/', { mode: 'no-cors' });
      // If fetch doesn't throw, server is responding on port 8081
      setPgwebHealth('UP');
    } catch {
      setPgwebHealth('DOWN');
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedQuery(id);
    setTimeout(() => setCopiedQuery(null), 2000);
  };

  return (
    <div className="min-h-screen bg-background p-4 sm:p-6 lg:p-8">
      {/* Top Header */}
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Link
                to="/builder/dashboard"
                className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
              >
                <ArrowLeft className="size-3.5" />
                Back to Dashboard
              </Link>
              <span className="text-muted-foreground">•</span>
              <Badge variant="outline" className="text-[11px] font-mono text-emerald-700 bg-emerald-50 dark:bg-emerald-950/40">
                localhost developer portal
              </Badge>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Backend & Database Portal
            </h1>
            <p className="text-sm text-muted-foreground">
              Direct visibility into running Spring Boot services, PostgreSQL tables, S3 media, and mail servers.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={checkHealth}
              className="gap-1.5 text-xs h-8"
            >
              <RefreshCw className="size-3.5" />
              Refresh Status
            </Button>
            <Button asChild size="sm" className="gap-1.5 text-xs h-8 bg-emerald-700 hover:bg-emerald-800">
              <a href="http://localhost:8081" target="_blank" rel="noopener noreferrer">
                <Database className="size-3.5" />
                Open Database Studio
                <ExternalLink className="size-3" />
              </a>
            </Button>
          </div>
        </div>

        {/* Live Status Indicators Banner */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Card className="p-3 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Spring Boot API</span>
              <Badge
                variant="outline"
                className={`text-[10px] ${
                  backendHealth === 'UP'
                    ? 'border-emerald-500 text-emerald-700 bg-emerald-50/50'
                    : backendHealth === 'CHECKING'
                    ? 'border-amber-500 text-amber-700'
                    : 'border-destructive text-destructive'
                }`}
              >
                {backendHealth === 'UP' ? 'Online' : backendHealth === 'CHECKING' ? 'Checking...' : 'Offline'}
              </Badge>
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="font-mono text-sm font-semibold">Port 8080</span>
            </div>
          </Card>

          <Card className="p-3 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Database Studio</span>
              <Badge
                variant="outline"
                className={`text-[10px] ${
                  pgwebHealth === 'UP'
                    ? 'border-emerald-500 text-emerald-700 bg-emerald-50/50'
                    : 'border-destructive text-destructive'
                }`}
              >
                {pgwebHealth === 'UP' ? 'Online' : 'Offline'}
              </Badge>
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="font-mono text-sm font-semibold">Port 8081</span>
            </div>
          </Card>

          <Card className="p-3 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">PostgreSQL 16</span>
              <Badge variant="outline" className="border-emerald-500 text-emerald-700 bg-emerald-50/50 text-[10px]">
                59 Tables
              </Badge>
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="font-mono text-sm font-semibold">Port 5432</span>
            </div>
          </Card>

          <Card className="p-3 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Vite Frontend</span>
              <Badge variant="outline" className="border-emerald-500 text-emerald-700 bg-emerald-50/50 text-[10px]">
                Active
              </Badge>
            </div>
            <div className="mt-1 flex items-baseline gap-1">
              <span className="font-mono text-sm font-semibold">Port 5173</span>
            </div>
          </Card>
        </div>

        {/* Main Portal Tabs */}
        <Tabs defaultValue="services" className="space-y-4">
          <TabsList>
            <TabsTrigger value="services" className="gap-1.5 text-xs">
              <Layers className="size-3.5" />
              Localhost Services
            </TabsTrigger>
            <TabsTrigger value="embedded-db" className="gap-1.5 text-xs">
              <Database className="size-3.5" />
              Live Database Studio
            </TabsTrigger>
            <TabsTrigger value="sql-quickstart" className="gap-1.5 text-xs">
              <Terminal className="size-3.5" />
              SQL Quickstart & Tables
            </TabsTrigger>
          </TabsList>

          {/* TAB 1: Services Cards */}
          <TabsContent value="services" className="space-y-4">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {SERVICES.map((srv) => {
                const Icon = srv.icon;
                return (
                  <Card key={srv.title} className="flex flex-col justify-between overflow-hidden shadow-xs hover:border-emerald-600/50 transition-colors">
                    <CardHeader className="pb-3">
                      <div className="flex items-center justify-between">
                        <div className="flex size-9 items-center justify-center rounded-lg bg-emerald-700/10 text-emerald-700">
                          <Icon className="size-5" />
                        </div>
                        <Badge variant="secondary" className="text-[10px] font-mono">
                          :{srv.port}
                        </Badge>
                      </div>
                      <CardTitle className="text-base font-semibold pt-2">{srv.title}</CardTitle>
                      <CardDescription className="text-xs line-clamp-2">
                        {srv.description}
                      </CardDescription>
                    </CardHeader>

                    <CardContent className="pb-3 text-xs space-y-2">
                      <div className="rounded-md bg-muted/60 px-2.5 py-1.5 font-mono text-[11px] text-muted-foreground flex items-center justify-between">
                        <span className="truncate">{srv.url}</span>
                      </div>
                      {srv.credentials && (
                        <p className="text-[11px] text-amber-700 dark:text-amber-400 font-mono">
                          🔑 {srv.credentials}
                        </p>
                      )}
                    </CardContent>

                    <CardFooter className="pt-0">
                      <Button asChild className="w-full gap-1.5 text-xs" variant={srv.category === 'database' ? 'default' : 'outline'}>
                        <a href={srv.url} target="_blank" rel="noopener noreferrer">
                          Open in New Tab
                          <ExternalLink className="size-3" />
                        </a>
                      </Button>
                    </CardFooter>
                  </Card>
                );
              })}
            </div>
          </TabsContent>

          {/* TAB 2: Embedded Database Studio (pgweb iframe) */}
          <TabsContent value="embedded-db" className="space-y-3">
            <div className="flex items-center justify-between rounded-lg border bg-muted/30 p-2.5 text-xs">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-emerald-600" />
                <span>
                  Connected to PostgreSQL database: <strong className="font-mono">shardeya</strong> on port 5432
                </span>
              </div>
              <Button asChild size="sm" variant="ghost" className="h-7 text-xs gap-1">
                <a href="http://localhost:8081" target="_blank" rel="noopener noreferrer">
                  <Maximize2 className="size-3" />
                  Full Screen Studio
                </a>
              </Button>
            </div>

            <div className="overflow-hidden rounded-xl border bg-background shadow-xs">
              <iframe
                src="http://localhost:8081"
                title="PostgreSQL Web Studio"
                className="h-[680px] w-full border-none"
              />
            </div>
          </TabsContent>

          {/* TAB 3: SQL Quickstart & Tables Reference */}
          <TabsContent value="sql-quickstart" className="space-y-4">
            <Card className="shadow-xs">
              <CardHeader>
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <Terminal className="size-4 text-emerald-600" />
                  Database CLI Access
                </CardTitle>
                <CardDescription className="text-xs">
                  Run this command in your terminal to open the interactive PostgreSQL shell directly:
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex items-center justify-between rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100">
                  <code>docker exec -it shardeya-postgres-1 psql -U shardeya -d shardeya</code>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => handleCopy('docker exec -it shardeya-postgres-1 psql -U shardeya -d shardeya', 'cli')}
                    className="size-7 p-0 text-slate-300 hover:text-white"
                  >
                    {copiedQuery === 'cli' ? <Check className="size-3.5 text-emerald-400" /> : <Copy className="size-3.5" />}
                  </Button>
                </div>
              </CardContent>
            </Card>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <Card className="shadow-xs">
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-semibold">1. Projects & Map Coordinates Query</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-start justify-between rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100">
                    <pre className="overflow-x-auto text-[11px] leading-relaxed">
{`SELECT id, name, city, latitude, longitude
FROM project
WHERE deleted_at IS NULL;`}
                    </pre>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy('SELECT id, name, city, latitude, longitude FROM project WHERE deleted_at IS NULL;', 'q1')}
                      className="size-7 p-0 text-slate-300 hover:text-white"
                    >
                      {copiedQuery === 'q1' ? <Check className="size-3.5 text-emerald-400" /> : <Copy className="size-3.5" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <Card className="shadow-xs">
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-semibold">2. Users & Organizations Query</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-start justify-between rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100">
                    <pre className="overflow-x-auto text-[11px] leading-relaxed">
{`SELECT u.email, u.mobile, r.code AS role, o.name AS org_name
FROM app_user u
JOIN role r ON u.role_id = r.id
JOIN organization o ON u.org_id = o.id;`}
                    </pre>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy('SELECT u.email, u.mobile, r.code AS role, o.name AS org_name FROM app_user u JOIN role r ON u.role_id = r.id JOIN organization o ON u.org_id = o.id;', 'q2')}
                      className="size-7 p-0 text-slate-300 hover:text-white"
                    >
                      {copiedQuery === 'q2' ? <Check className="size-3.5 text-emerald-400" /> : <Copy className="size-3.5" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <Card className="shadow-xs">
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-semibold">3. Plot Inventory Summary</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-start justify-between rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100">
                    <pre className="overflow-x-auto text-[11px] leading-relaxed">
{`SELECT status, COUNT(*) AS count
FROM plot
GROUP BY status;`}
                    </pre>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy('SELECT status, COUNT(*) AS count FROM plot GROUP BY status;', 'q3')}
                      className="size-7 p-0 text-slate-300 hover:text-white"
                    >
                      {copiedQuery === 'q3' ? <Check className="size-3.5 text-emerald-400" /> : <Copy className="size-3.5" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <Card className="shadow-xs">
                <CardHeader className="pb-3">
                  <CardTitle className="text-sm font-semibold">4. Broker Syndicate & Network Tree</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-start justify-between rounded-lg bg-slate-900 p-3 font-mono text-xs text-slate-100">
                    <pre className="overflow-x-auto text-[11px] leading-relaxed">
{`SELECT name, agency_name, designation, active_status
FROM broker_partner
WHERE deleted_at IS NULL;`}
                    </pre>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => handleCopy('SELECT name, agency_name, designation, active_status FROM broker_partner WHERE deleted_at IS NULL;', 'q4')}
                      className="size-7 p-0 text-slate-300 hover:text-white"
                    >
                      {copiedQuery === 'q4' ? <Check className="size-3.5 text-emerald-400" /> : <Copy className="size-3.5" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
