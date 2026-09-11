import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { FormError } from '@/components/forms/FormError';
import { AreaInput } from '@/components/forms/AreaInput';
import { ImageUploader } from '@/components/media/ImageUploader';
import { MapLocationPicker } from '@/components/maps/MapLocationPicker';
import { EntitlementGuard } from '@/components/feedback/EntitlementGuard';
import { INDIAN_STATES } from '@/lib/indianStates';
import { resolveErrorMessage } from '@/lib/api/errorMessage';
import { createProject, getProject, updateProject } from '../api/projectApi';
import { buildProjectSchema, PROJECT_FORM_STEP_FIELDS, type ProjectFormValues } from '../schemas';
import type { ProjectCreateRequest, ProjectType } from '../types';

const PROJECT_TYPES: ProjectType[] = ['RESIDENTIAL_PLOT_COLONY', 'APARTMENT', 'VILLA', 'COMMERCIAL', 'MIXED_USE'];
const STEP_KEYS = ['basics', 'location', 'area', 'media', 'review'] as const;

export function ProjectFormPage() {
  const { t, i18n } = useTranslation(['project', 'common', 'errors']);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const [step, setStep] = useState(0);

  const existing = useQuery({ queryKey: ['project', id], queryFn: () => getProject(id!), enabled: isEdit });

  const schema = buildProjectSchema(t);
  const form = useForm<ProjectFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      projectType: undefined,
      address: '',
      locality: '',
      city: '',
      stateCode: '',
      pincode: '',
      googleMapsUrl: '',
      latitude: null,
      longitude: null,
      totalAreaValue: undefined,
      totalAreaUnit: 'SQ_FT',
      declaredPlotCount: undefined,
      launchDate: '',
      expectedCompletionDate: '',
      description: '',
      reraNumber: '',
      coverMediaId: undefined,
      layoutMediaId: undefined,
      brochureMediaId: undefined,
    },
  });

  useEffect(() => {
    if (existing.data) {
      form.reset({
        name: existing.data.name,
        projectType: existing.data.projectType,
        address: existing.data.address,
        locality: existing.data.locality,
        city: existing.data.city,
        stateCode: existing.data.stateCode,
        pincode: existing.data.pincode ?? '',
        googleMapsUrl: existing.data.googleMapsUrl ?? '',
        latitude: existing.data.latitude ?? null,
        longitude: existing.data.longitude ?? null,
        totalAreaValue: existing.data.totalAreaValue,
        totalAreaUnit: existing.data.totalAreaUnit,
        declaredPlotCount: existing.data.declaredPlotCount,
        launchDate: existing.data.launchDate ?? '',
        expectedCompletionDate: existing.data.expectedCompletionDate ?? '',
        description: existing.data.description ?? '',
        reraNumber: existing.data.reraNumber ?? '',
        coverMediaId: existing.data.coverMediaId ?? undefined,
        layoutMediaId: existing.data.layoutMediaId ?? undefined,
        brochureMediaId: existing.data.brochureMediaId ?? undefined,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existing.data]);

  const mutation = useMutation({
    mutationFn: (values: ProjectFormValues) => {
      const req: ProjectCreateRequest = {
        ...values,
        pincode: values.pincode || undefined,
        googleMapsUrl: values.googleMapsUrl || undefined,
        latitude: values.latitude ?? null,
        longitude: values.longitude ?? null,
        launchDate: values.launchDate || undefined,
        expectedCompletionDate: values.expectedCompletionDate || undefined,
        description: values.description || undefined,
        reraNumber: values.reraNumber || undefined,
      };
      return isEdit ? updateProject(id!, req) : createProject(req);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      navigate(`/builder/projects/${result.id}`);
    },
  });

  const goNext = async () => {
    const fields = PROJECT_FORM_STEP_FIELDS[step];
    const valid = await form.trigger(fields.length ? fields : undefined);
    if (valid) setStep((s) => Math.min(s + 1, STEP_KEYS.length - 1));
  };
  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  const values = form.watch();
  const stateLabel = (code: string) => {
    const state = INDIAN_STATES.find((s) => s.code === code);
    if (!state) return code;
    return i18n.language === 'hi' ? state.nameHi : state.nameEn;
  };

  if (isEdit && existing.isLoading) {
    return <p className="text-sm text-muted-foreground">{t('common:placeholder.comingSoon')}</p>;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title={isEdit ? t('form.editTitle') : t('form.createTitle')}
        description={t('form.stepLabel', { current: step + 1, total: STEP_KEYS.length })}
      />

      <div className="mb-6 flex gap-1">
        {STEP_KEYS.map((key, i) => (
          <div key={key} className={`h-1.5 flex-1 rounded-full ${i <= step ? 'bg-primary' : 'bg-muted'}`} />
        ))}
      </div>

      <form
        onSubmit={form.handleSubmit((v) => mutation.mutate(v))}
        className="space-y-4"
        noValidate
      >
        {step === 0 && (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t('form.fields.name')}</Label>
              <Input id="name" placeholder={t('form.placeholders.name')} {...form.register('name')} />
              <FormError message={form.formState.errors.name?.message} />
            </div>
            <div className="space-y-2">
              <Label>{t('form.fields.projectType')}</Label>
              <Select
                value={form.watch('projectType')}
                onValueChange={(v) => form.setValue('projectType', v as ProjectType, { shouldValidate: true })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder={t('form.fields.projectType')} />
                </SelectTrigger>
                <SelectContent>
                  {PROJECT_TYPES.map((pt) => (
                    <SelectItem key={pt} value={pt}>
                      {t(`type.${pt}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FormError message={form.formState.errors.projectType?.message} />
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="address">{t('form.fields.address')}</Label>
              <Textarea id="address" placeholder={t('form.placeholders.address')} {...form.register('address')} />
              <FormError message={form.formState.errors.address?.message} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="locality">{t('form.fields.locality')}</Label>
                <Input id="locality" placeholder={t('form.placeholders.locality')} {...form.register('locality')} />
                <FormError message={form.formState.errors.locality?.message} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="city">{t('form.fields.city')}</Label>
                <Input id="city" placeholder={t('form.placeholders.city')} {...form.register('city')} />
                <FormError message={form.formState.errors.city?.message} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('form.fields.stateCode')}</Label>
                <Select value={values.stateCode} onValueChange={(v) => form.setValue('stateCode', v, { shouldValidate: true })}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder={t('form.placeholders.stateCode')}>
                      {values.stateCode ? stateLabel(values.stateCode) : undefined}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {INDIAN_STATES.map((s) => (
                      <SelectItem key={s.code} value={s.code}>
                        {i18n.language === 'hi' ? s.nameHi : s.nameEn}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormError message={form.formState.errors.stateCode?.message} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="pincode">{t('form.fields.pincode')}</Label>
                <Input id="pincode" placeholder={t('form.placeholders.pincode')} {...form.register('pincode')} />
                <FormError message={form.formState.errors.pincode?.message} />
              </div>
            </div>

            <div className="pt-2">
              <MapLocationPicker
                latitude={values.latitude}
                longitude={values.longitude}
                initialCity={values.city || values.locality}
                onChange={(lat, lng, mapsUrl) => {
                  form.setValue('latitude', lat, { shouldValidate: true });
                  form.setValue('longitude', lng, { shouldValidate: true });
                  if (mapsUrl) {
                    form.setValue('googleMapsUrl', mapsUrl, { shouldValidate: true });
                  }
                }}
              />
              <FormError message={form.formState.errors.latitude?.message ?? form.formState.errors.longitude?.message} />
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('form.fields.totalArea')}</Label>
              <AreaInput
                value={values.totalAreaValue}
                unit={values.totalAreaUnit}
                onValueChange={(v) => form.setValue('totalAreaValue', v ?? (undefined as unknown as number), { shouldValidate: true })}
                onUnitChange={(u) => form.setValue('totalAreaUnit', u, { shouldValidate: true })}
                stateCode={values.stateCode}
                error={form.formState.errors.totalAreaValue?.message ?? form.formState.errors.totalAreaUnit?.message}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="declaredPlotCount">{t('form.fields.declaredPlotCount')}</Label>
              <Input
                id="declaredPlotCount"
                type="number"
                inputMode="numeric"
                min={1}
                {...form.register('declaredPlotCount', { valueAsNumber: true })}
              />
              <FormError message={form.formState.errors.declaredPlotCount?.message} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="launchDate">{t('form.fields.launchDate')}</Label>
                <Input id="launchDate" type="date" {...form.register('launchDate')} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="expectedCompletionDate">{t('form.fields.expectedCompletionDate')}</Label>
                <Input id="expectedCompletionDate" type="date" {...form.register('expectedCompletionDate')} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t('form.fields.description')}</Label>
              <Textarea id="description" placeholder={t('form.placeholders.description')} {...form.register('description')} />
              <FormError message={form.formState.errors.description?.message} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="reraNumber">{t('form.fields.reraNumber')}</Label>
              <Input id="reraNumber" placeholder={t('form.placeholders.reraNumber')} {...form.register('reraNumber')} />
              <FormError message={form.formState.errors.reraNumber?.message} />
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-6">
            <ImageUploader
              label={t('form.coverPhoto')}
              mediaId={values.coverMediaId}
              purpose="project_cover"
              onUploaded={(mediaId) => form.setValue('coverMediaId', mediaId)}
              onRemove={() => form.setValue('coverMediaId', undefined)}
            />
            <ImageUploader
              label={t('form.layoutPlan')}
              mediaId={values.layoutMediaId}
              purpose="project_layout"
              onUploaded={(mediaId) => form.setValue('layoutMediaId', mediaId)}
              onRemove={() => form.setValue('layoutMediaId', undefined)}
            />
            <ImageUploader
              label={t('form.brochure')}
              mediaId={values.brochureMediaId}
              purpose="project_brochure"
              onUploaded={(mediaId) => form.setValue('brochureMediaId', mediaId)}
              onRemove={() => form.setValue('brochureMediaId', undefined)}
              accept="image/*,application/pdf"
            />
          </div>
        )}

        {step === 4 && (
          <div className="space-y-3 rounded-md border border-border p-4">
            <p className="text-sm font-medium text-foreground">{t('form.reviewTitle')}</p>
            <p className="text-sm text-muted-foreground">{t('form.reviewHint')}</p>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <dt className="text-muted-foreground">{t('form.fields.name')}</dt>
              <dd>{values.name}</dd>
              <dt className="text-muted-foreground">{t('form.fields.projectType')}</dt>
              <dd>{values.projectType ? t(`type.${values.projectType}`) : ''}</dd>
              <dt className="text-muted-foreground">{t('form.fields.address')}</dt>
              <dd>{values.address}</dd>
              <dt className="text-muted-foreground">{t('form.fields.city')}</dt>
              <dd>
                {values.city}, {stateLabel(values.stateCode)}
              </dd>
              <dt className="text-muted-foreground">{t('form.fields.totalArea')}</dt>
              <dd>
                {values.totalAreaValue} {values.totalAreaUnit}
              </dd>
              <dt className="text-muted-foreground">{t('form.fields.declaredPlotCount')}</dt>
              <dd>{values.declaredPlotCount}</dd>
              {values.latitude && values.longitude && (
                <>
                  <dt className="text-muted-foreground">Map Location</dt>
                  <dd className="font-mono text-xs text-emerald-700 font-medium">
                    {values.latitude.toFixed(5)}° N, {values.longitude.toFixed(5)}° E
                  </dd>
                </>
              )}
            </dl>
          </div>
        )}

        {mutation.isError && (
          <>
            <EntitlementGuard error={mutation.error} />
            <FormError message={resolveErrorMessage(mutation.error)} />
          </>
        )}

        <div className="flex justify-between pt-2">
          <Button type="button" variant="outline" onClick={goBack} disabled={step === 0}>
            {t('common:actions.back')}
          </Button>
          {step < STEP_KEYS.length - 1 ? (
            // Distinct `key`s from the submit button below force React to
            // create a fresh DOM node when the step changes, rather than
            // reusing/mutating this same-position <button> in place. Without
            // them, a click that both fires goNext() AND (synchronously,
            // same tick) re-renders this button as type="submit" for the new
            // step causes the browser's native click activation behavior to
            // treat the CURRENT click as a form submit -- auto-submitting on
            // the very click that was only meant to advance the step, with
            // no second click ever happening. Caught only by driving the
            // wizard through an actual browser (mutationFn fired immediately
            // after the step 3->4 transition, not from any click on the
            // real "Create Project" button) -- invisible from code review.
            <Button key="next-button" type="button" onClick={goNext}>
              {t('common:actions.next')}
            </Button>
          ) : (
            <Button key="submit-button" type="submit" disabled={mutation.isPending}>
              {mutation.isPending
                ? isEdit
                  ? t('form.savingChanges')
                  : t('form.submitting')
                : isEdit
                  ? t('form.saveChanges')
                  : t('form.submit')}
            </Button>
          )}
        </div>
      </form>
    </div>
  );
}
