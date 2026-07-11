export interface GitHubRelease {
  tag_name: string;
  name: string;
  published_at: string;
  body: string;
  html_url: string;
}

export interface FeatureItem {
  id: string;
  title: string;
  description: string;
  iconName: string;
}

export interface ValueProposition {
  title: string;
  description: string;
  iconName: string;
}

export interface DocSection {
  id: string;
  title: string;
  description: string;
  codeSnippet?: string;
  language?: string;
}
