import { Link, NavLink } from "react-router-dom";
import type { ReactNode } from "react";

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/" className="brand">
            作業映像ポータル
          </Link>
          <nav className="nav-links">
            <NavLink
              to="/"
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              作業動画一覧
            </NavLink>
          </nav>
        </div>
      </header>
      <main className="app-main">{children}</main>
    </div>
  );
}
