import { Navigate, Route, Routes } from "react-router-dom";
import "./App.css";
import { Layout } from "./components/Layout";
import SegmentPage from "./pages/SegmentPage";
import WorkDetailPage from "./pages/WorkDetailPage";
import WorkListPage from "./pages/WorkListPage";

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<WorkListPage />} />
        <Route path="/works/:workId" element={<WorkDetailPage />} />
        <Route path="/segments/:segmentId" element={<SegmentPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

export default App;
