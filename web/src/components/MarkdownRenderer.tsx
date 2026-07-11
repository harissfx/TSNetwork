import React from "react";

interface MarkdownRendererProps {
  content: string;
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  if (!content) return <p className="text-zinc-400 italic">Tidak ada catatan rilis.</p>;

  const lines = content.split("\n");

  const parseInline = (text: string): React.ReactNode => {
    const parts: React.ReactNode[] = [];
    let current = text;
    
    // Regex for bold **text**, code `text`, and links [label](url)
    const regex = /(\*\*.*?\*\*|`.*?`|\[.*?\]\(.*?\))/g;
    let match;
    let lastIndex = 0;

    while ((match = regex.exec(text)) !== null) {
      const offset = match.index;
      const token = match[0];

      // Append text preceding the token
      if (offset > lastIndex) {
        parts.push(text.substring(lastIndex, offset));
      }

      if (token.startsWith("**") && token.endsWith("**")) {
        parts.push(
          <strong key={offset} className="font-semibold text-white">
            {token.slice(2, -2)}
          </strong>
        );
      } else if (token.startsWith("`") && token.endsWith("`")) {
        parts.push(
          <code key={offset} className="px-1.5 py-0.5 rounded bg-zinc-800 text-brand text-xs font-mono">
            {token.slice(1, -1)}
          </code>
        );
      } else if (token.startsWith("[") && token.includes("](")) {
        const closingBracket = token.indexOf("]");
        const label = token.slice(1, closingBracket);
        const url = token.slice(closingBracket + 2, -1);
        parts.push(
          <a
            key={offset}
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-brand hover:text-brand-light underline font-medium"
          >
            {label}
          </a>
        );
      }

      lastIndex = regex.lastIndex;
    }

    if (lastIndex < text.length) {
      parts.push(text.substring(lastIndex));
    }

    return parts.length > 0 ? <>{parts}</> : text;
  };

  return (
    <div className="space-y-2 text-zinc-300 text-sm leading-relaxed">
      {lines.map((line, idx) => {
        const trimmed = line.trim();

        // Headings
        if (trimmed.startsWith("### ")) {
          return (
            <h4 key={idx} className="text-base font-bold text-white mt-4 mb-2 border-b border-zinc-800 pb-1">
              {parseInline(trimmed.slice(4))}
            </h4>
          );
        }
        if (trimmed.startsWith("## ")) {
          return (
            <h3 key={idx} className="text-lg font-bold text-white mt-5 mb-3">
              {parseInline(trimmed.slice(3))}
            </h3>
          );
        }
        if (trimmed.startsWith("# ")) {
          return (
            <h2 key={idx} className="text-xl font-extrabold text-brand mt-6 mb-4">
              {parseInline(trimmed.slice(2))}
            </h2>
          );
        }

        // Unordered lists
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
          return (
            <div key={idx} className="flex items-start gap-2 pl-4">
              <span className="text-brand mt-1.5 block h-1.5 w-1.5 shrink-0 rounded-full" />
              <span className="text-zinc-300">{parseInline(trimmed.slice(2))}</span>
            </div>
          );
        }

        // Ordered lists
        if (/^\d+\.\s/.test(trimmed)) {
          const dotIdx = trimmed.indexOf(".");
          const num = trimmed.slice(0, dotIdx);
          const textVal = trimmed.slice(dotIdx + 1).trim();
          return (
            <div key={idx} className="flex items-start gap-2 pl-4">
              <span className="text-brand font-mono text-xs mt-0.5 font-bold">{num}.</span>
              <span className="text-zinc-300">{parseInline(textVal)}</span>
            </div>
          );
        }

        // Blockquotes
        if (trimmed.startsWith("> ")) {
          return (
            <blockquote key={idx} className="border-l-4 border-brand bg-zinc-900/50 px-4 py-2 my-2 rounded-r italic text-zinc-400">
              {parseInline(trimmed.slice(2))}
            </blockquote>
          );
        }

        // Spacer lines
        if (trimmed === "") {
          return <div key={idx} className="h-2" />;
        }

        // Standard paragraphs
        return (
          <p key={idx} className="text-zinc-300">
            {parseInline(line)}
          </p>
        );
      })}
    </div>
  );
}
