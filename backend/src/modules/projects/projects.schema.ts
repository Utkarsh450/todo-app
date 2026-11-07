import { z } from 'zod';

export const hexColor = z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'must be a #RRGGBB colour');

export const projectSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  color: z.string(),
  createdAt: z.date(),
});

export const createProjectBody = z.object({
  name: z.string().trim().min(1).max(40),
  color: hexColor,
});

export const updateProjectBody = createProjectBody.partial().refine((v) => Object.keys(v).length > 0, 'at least one field is required');

export const idParams = z.object({ id: z.uuid() });

export type CreateProjectInput = z.infer<typeof createProjectBody>;
export type UpdateProjectInput = z.infer<typeof updateProjectBody>;

/** Default projects from the Paper System design. */
export const DEFAULT_PROJECTS: readonly CreateProjectInput[] = [
  { name: 'Design', color: '#935EDF' },
  { name: 'Engineering', color: '#2784D5' },
  { name: 'Learning', color: '#DF911A' },
  { name: 'Personal', color: '#2FA465' },
  { name: 'Work', color: '#E05D3D' },
];
