export type Role = 'ORG_ADMIN' | 'TEAM_MANAGER' | 'MEMBER' | 'VIEWER'

export interface Me {
  userId: string
  membershipId: string
  organizationId: string
  username: string
  displayName: string
  roles: Role[]
  passwordChangeRequired: boolean
}

export interface Board {
  id: string
  name: string
  slug: string
  description?: string
  icon: string
  color: string
  sortOrder: number
  active: boolean
  postCount: number
}

export interface Attachment {
  id: string
  fileName: string
  contentType: string
  fileSize: number
  url: string
}

export interface ForumComment {
  id: string
  parentId?: string
  authorDisplayName: string
  content: string
  createdAt: string
}

export interface PostSummary {
  id: string
  boardId: string
  boardName: string
  boardSlug: string
  authorDisplayName: string
  postType: 'POST' | 'ANNOUNCEMENT'
  title: string
  excerpt?: string
  tags: string[]
  pinned: boolean
  locked: boolean
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
}

export interface PostDetail extends PostSummary {
  content: string
  updatedAt: string
  attachments: Attachment[]
  comments: ForumComment[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
  pages: number
}

export interface Report {
  id: string
  postId: string
  postTitle: string
  reporterDisplayName: string
  reason: string
  status: string
  createdAt: string
}

export interface Overview {
  publishedPosts: number
  comments: number
  likes: number
  openReports: number
  members: number
  popularPosts: PostSummary[]
  announcementReads: Array<{ postId: string; title: string; readCount: number; totalMembers: number }>
}
